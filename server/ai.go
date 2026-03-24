package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/url"
)

// System prompts.

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

// Request/response types for AI endpoints.

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
	MeshState string `json:"mesh_state"`
	QuizScore *int   `json:"quiz_score,omitempty"`
	QuizTotal *int   `json:"quiz_total,omitempty"`
}

type SummaryResponse struct {
	Summary string `json:"summary"`
}

// isPrivateIP returns true if the IP is in a private/reserved range (RFC1918, link-local, loopback).
func isPrivateIP(ip net.IP) bool {
	privateRanges := []string{
		"10.0.0.0/8",
		"172.16.0.0/12",
		"192.168.0.0/16",
		"127.0.0.0/8",
		"169.254.0.0/16",
		"::1/128",
		"fc00::/7",
		"fe80::/10",
	}
	for _, cidr := range privateRanges {
		_, network, err := net.ParseCIDR(cidr)
		if err != nil {
			continue
		}
		if network.Contains(ip) {
			return true
		}
	}
	return false
}

// validateLLMBaseURL checks that the URL has http/https scheme and does not point to a private IP.
func validateLLMBaseURL(rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil {
		return fmt.Errorf("invalid URL: %w", err)
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return fmt.Errorf("URL scheme must be http or https, got %q", u.Scheme)
	}
	host := u.Hostname()
	ip := net.ParseIP(host)
	if ip != nil && isPrivateIP(ip) {
		return fmt.Errorf("private/reserved IP addresses are not allowed")
	}
	// If it's a hostname, resolve and check all IPs
	if ip == nil {
		addrs, err := net.LookupIP(host)
		if err != nil {
			return fmt.Errorf("cannot resolve host %q: %w", host, err)
		}
		for _, addr := range addrs {
			if isPrivateIP(addr) {
				return fmt.Errorf("host %q resolves to private IP %s", host, addr)
			}
		}
	}
	return nil
}

// Route registration.

func registerAIRoutes(mux *http.ServeMux, cfg *LLMConfig, summaryCache *SummaryCache) {

	// GET /ai/config
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

	// PUT /ai/config
	mux.HandleFunc("PUT /ai/config", func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
		var body struct {
			BaseURL string `json:"llm_base_url"`
			Model   string `json:"llm_model"`
			APIKey  string `json:"llm_api_key"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}
		if body.BaseURL != "" {
			if err := validateLLMBaseURL(body.BaseURL); err != nil {
				writeJSON(w, http.StatusBadRequest, map[string]string{"error": fmt.Sprintf("invalid base URL: %v", err)})
				return
			}
		}
		cfg.Update(body.BaseURL, body.Model, body.APIKey)
		log.Printf("LLM config updated: base_url=%s model=%s has_key=%v", body.BaseURL, body.Model, body.APIKey != "")
		writeJSON(w, http.StatusOK, map[string]string{"status": "updated"})
	})

	// POST /ai/narrate
	mux.HandleFunc("POST /ai/narrate", func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
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

	// POST /ai/what-if
	mux.HandleFunc("POST /ai/what-if", func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
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

	// POST /ai/summary (cached)
	mux.HandleFunc("POST /ai/summary", func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
		var req SummaryRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}

		// Check cache first
		if cached, ok := summaryCache.Get(req); ok {
			log.Printf("summary cache hit")
			writeJSON(w, http.StatusOK, cached)
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

		resp := SummaryResponse{Summary: content}
		summaryCache.Set(req, resp)
		writeJSON(w, http.StatusOK, resp)
	})

	// POST /ai/test
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
