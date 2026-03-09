package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

// ──────────────────────────────────────────────
//  LLM configuration (env vars + runtime PUT)
// ──────────────────────────────────────────────

type LLMConfig struct {
	mu       sync.RWMutex
	BaseURL  string `json:"llm_base_url"`
	Model    string `json:"llm_model"`
	APIKey   string `json:"llm_api_key,omitempty"`
}

func NewLLMConfig() *LLMConfig {
	cfg := &LLMConfig{
		BaseURL: "http://localhost:1234",
		Model:   "default",
	}
	if v := os.Getenv("LLM_BASE_URL"); v != "" {
		cfg.BaseURL = v
	}
	if v := os.Getenv("LLM_MODEL"); v != "" {
		cfg.Model = v
	}
	if v := os.Getenv("LLM_API_KEY"); v != "" {
		cfg.APIKey = v
	}
	return cfg
}

func (c *LLMConfig) Get() (baseURL, model, apiKey string) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.BaseURL, c.Model, c.APIKey
}

func (c *LLMConfig) Update(baseURL, model, apiKey string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if baseURL != "" {
		c.BaseURL = baseURL
	}
	if model != "" {
		c.Model = model
	}
	c.APIKey = apiKey // allow clearing
}

// ──────────────────────────────────────────────
//  OpenAI-compatible chat completion client
// ──────────────────────────────────────────────

var llmHTTPClient = &http.Client{Timeout: 60 * time.Second}

type chatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatRequest struct {
	Model       string        `json:"model"`
	Messages    []chatMessage `json:"messages"`
	MaxTokens   int           `json:"max_tokens"`
	Temperature float64       `json:"temperature"`
}

type chatChoice struct {
	Message chatMessage `json:"message"`
}

type chatResponse struct {
	Choices []chatChoice `json:"choices"`
}

func callLLM(cfg *LLMConfig, messages []chatMessage, maxTokens int) (string, error) {
	baseURL, model, apiKey := cfg.Get()

	body := chatRequest{
		Model:       model,
		Messages:    messages,
		MaxTokens:   maxTokens,
		Temperature: 0.7,
	}
	payload, _ := json.Marshal(body)

	req, err := http.NewRequest("POST", baseURL+"/v1/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+apiKey)
	}

	resp, err := llmHTTPClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("llm request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("llm returned %d: %s", resp.StatusCode, string(respBody[:min(len(respBody), 200)]))
	}

	var cr chatResponse
	if err := json.Unmarshal(respBody, &cr); err != nil {
		return "", fmt.Errorf("parse response: %w", err)
	}
	if len(cr.Choices) == 0 {
		return "", fmt.Errorf("no choices in response")
	}
	return cr.Choices[0].Message.Content, nil
}

// ──────────────────────────────────────────────
//  System prompts (centralized, updatable)
// ──────────────────────────────────────────────

const narratorSystemPrompt = `You are a networking protocol tutor embedded in a mesh networking education app.
The student is running a live peer-to-peer mesh and you observe protocol events in real-time.

When given protocol events, provide a SHORT (1-3 sentence) educational explanation of:
1. What just happened technically
2. Why it happened (cause)
3. What this teaches about real-world networking

Use the student's actual data (peer names, RTT values, packet numbers).
Be concise — this appears as an overlay on their screen.
Tone: friendly, informative, like a lab assistant looking over their shoulder.`

const whatIfSystemPrompt = `You are a networking lab assistant. The student has a live mesh network and wants to explore
"what if" scenarios. You have their current mesh state below.

Answer their question by:
1. Explaining what would theoretically happen, referencing their ACTUAL mesh data
2. If possible, suggest a hands-on experiment they can try with specific parameter changes
3. Connect to real-world networking concepts

Keep answers concise (3-5 sentences). Use their peer names and actual RTT/loss values.
If suggesting an experiment, format as: "Try this: [specific action]"`

const summarySystemPrompt = `You are a networking education tutor reviewing a student's mesh networking lab session.
Generate a personalized learning summary that:

1. Summarizes what they did (duration, peers, protocols used)
2. Highlights 2-3 key networking concepts they experienced firsthand
3. Explains what they learned from specific events (retransmissions = TCP reliability, collisions = shared medium access)
4. Suggests 1-2 things to try next session

Reference their actual data. Be encouraging but educational.
Format with clear sections and bullet points. Keep it under 300 words.`

// ──────────────────────────────────────────────
//  Request/response types for AI endpoints
// ──────────────────────────────────────────────

type NarrateRequest struct {
	Events    []string `json:"events"`
	MeshState string   `json:"mesh_state"`
}

type NarrateResponse struct {
	Title       string `json:"title"`
	Explanation string `json:"explanation"`
}

type WhatIfRequest struct {
	Question  string        `json:"question"`
	MeshState string        `json:"mesh_state"`
	History   []WhatIfEntry `json:"history,omitempty"`
}

type WhatIfEntry struct {
	Question string `json:"question"`
	Answer   string `json:"answer"`
}

type WhatIfResponse struct {
	Answer string `json:"answer"`
}

type SummaryRequest struct {
	MeshState  string `json:"mesh_state"`
	QuizScore  *int   `json:"quiz_score,omitempty"`
	QuizTotal  *int   `json:"quiz_total,omitempty"`
}

type SummaryResponse struct {
	Summary string `json:"summary"`
}

// ──────────────────────────────────────────────
//  Route registration
// ──────────────────────────────────────────────

func registerAIRoutes(mux *http.ServeMux, cfg *LLMConfig) {

	// GET /ai/config — return current LLM config (API key masked)
	mux.HandleFunc("GET /ai/config", func(w http.ResponseWriter, r *http.Request) {
		baseURL, model, apiKey := cfg.Get()
		masked := ""
		if apiKey != "" {
			masked = apiKey[:min(4, len(apiKey))] + "****"
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"llm_base_url": baseURL,
			"llm_model":    model,
			"has_api_key":  apiKey != "",
			"api_key_hint": masked,
		})
	})

	// PUT /ai/config — update LLM config at runtime
	mux.HandleFunc("PUT /ai/config", func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			BaseURL string `json:"llm_base_url"`
			Model   string `json:"llm_model"`
			APIKey  string `json:"llm_api_key"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}
		cfg.Update(body.BaseURL, body.Model, body.APIKey)
		log.Printf("LLM config updated: base_url=%s model=%s has_key=%v", body.BaseURL, body.Model, body.APIKey != "")
		writeJSON(w, http.StatusOK, map[string]string{"status": "updated"})
	})

	// POST /ai/narrate — explain protocol events
	mux.HandleFunc("POST /ai/narrate", func(w http.ResponseWriter, r *http.Request) {
		var req NarrateRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}
		if len(req.Events) == 0 {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "events required"})
			return
		}

		eventText := ""
		for _, e := range req.Events {
			eventText += "- " + e + "\n"
		}

		userPrompt := fmt.Sprintf("Current mesh state:\n%s\n\nRecent protocol events to explain:\n%s", req.MeshState, eventText)

		content, err := callLLM(cfg, []chatMessage{
			{Role: "system", Content: narratorSystemPrompt},
			{Role: "user", Content: userPrompt},
		}, 200)

		if err != nil {
			log.Printf("narrate LLM error: %v", err)
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}

		writeJSON(w, http.StatusOK, NarrateResponse{
			Title:       "Protocol Insight",
			Explanation: content,
		})
	})

	// POST /ai/what-if — answer what-if questions
	mux.HandleFunc("POST /ai/what-if", func(w http.ResponseWriter, r *http.Request) {
		var req WhatIfRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}
		if req.Question == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "question required"})
			return
		}

		messages := []chatMessage{
			{Role: "system", Content: whatIfSystemPrompt},
			{Role: "user", Content: "Current mesh state:\n" + req.MeshState},
			{Role: "assistant", Content: "I can see your mesh network state. What would you like to explore?"},
		}

		// Append conversation history
		for _, h := range req.History {
			messages = append(messages, chatMessage{Role: "user", Content: h.Question})
			messages = append(messages, chatMessage{Role: "assistant", Content: h.Answer})
		}

		messages = append(messages, chatMessage{Role: "user", Content: req.Question})

		content, err := callLLM(cfg, messages, 400)
		if err != nil {
			log.Printf("what-if LLM error: %v", err)
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}

		writeJSON(w, http.StatusOK, WhatIfResponse{Answer: content})
	})

	// POST /ai/summary — generate post-session summary
	mux.HandleFunc("POST /ai/summary", func(w http.ResponseWriter, r *http.Request) {
		var req SummaryRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}

		userContent := "Here's my session data:\n" + req.MeshState
		if req.QuizScore != nil && req.QuizTotal != nil {
			userContent += fmt.Sprintf("\n\nQuiz results: %d/%d correct", *req.QuizScore, *req.QuizTotal)
		}

		content, err := callLLM(cfg, []chatMessage{
			{Role: "system", Content: summarySystemPrompt},
			{Role: "user", Content: userContent},
		}, 600)

		if err != nil {
			log.Printf("summary LLM error: %v", err)
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}

		writeJSON(w, http.StatusOK, SummaryResponse{Summary: content})
	})

	// POST /ai/test — test LLM connectivity
	mux.HandleFunc("POST /ai/test", func(w http.ResponseWriter, r *http.Request) {
		content, err := callLLM(cfg, []chatMessage{
			{Role: "user", Content: "Say 'OK' if you can hear me."},
		}, 10)
		if err != nil {
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"response": content})
	})
}
