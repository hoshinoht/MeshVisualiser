package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/cache"
	"github.com/inf2007/inf2007-team07-2026/server/internal/llm"
	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
	"github.com/inf2007/inf2007-team07-2026/server/internal/quiz"
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

const quizSystemPrompt = `You are a networking quiz generator for a mesh networking education app.
The student has a live peer-to-peer mesh network. Use their ACTUAL session data to generate
personalised multiple-choice questions that test their understanding of what is happening.

Generate exactly 10 questions as a JSON array. Mix these categories:
- SESSION questions (3-4): Ask about their specific mesh state — topology type, peer count,
  leader identity, RTT values, packet loss rates, collision counts, protocols used.
  Use the real numbers from their session.
- CONCEPT questions (4-5): Ask about networking concepts relevant to what they've experienced.
  For example, if they had collisions, ask about CSMA/CD. If they used TCP, ask about
  acknowledgments and retransmission. If they have a star topology, ask about its properties.
- SCENARIO questions (2-3): "What would happen if..." questions based on their current state.
  E.g. "If your TCP packet loss increased from 10% to 50%, what would you observe?"

Each question MUST have exactly 4 options with exactly 1 correct answer.
Each question MUST include a short explanation (1-2 sentences) of why the correct answer is right.

Respond with ONLY a JSON array, no markdown fences, no extra text. Each element:
{"text":"question text","options":["A","B","C","D"],"correct":0,"category":"SESSION|CONCEPT|SCENARIO","explanation":"Why the answer is correct"}

where "correct" is the 0-based index of the correct option.`

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

type QuizRequest struct {
	MeshState string `json:"mesh_state"`
}

type QuizResponse struct {
	Questions []quiz.Question `json:"questions"`
	Source    string          `json:"source"` // "ai" or "static"
}

type SummaryRequest struct {
	MeshState string `json:"mesh_state"`
	QuizScore *int   `json:"quiz_score,omitempty"`
	QuizTotal *int   `json:"quiz_total,omitempty"`
}

type SummaryResponse struct {
	Summary string `json:"summary"`
}

type SummaryCache = cache.TTLCache[SummaryResponse]

func NewSummaryCache(ttl time.Duration, stop <-chan struct{}) *SummaryCache {
	return cache.NewTTLCache[SummaryResponse](ttl, stop)
}

func SummaryCacheKey(req SummaryRequest) string {
	extra := ""
	if req.QuizScore != nil && req.QuizTotal != nil {
		extra = fmt.Sprintf("%d/%d", *req.QuizScore, *req.QuizTotal)
	}
	return cache.HashKey(req.MeshState, extra)
}

type QuizCache = cache.TTLCache[QuizResponse]

func NewQuizCache(ttl time.Duration, stop <-chan struct{}) *QuizCache {
	return cache.NewTTLCache[QuizResponse](ttl, stop)
}

func QuizCacheKey(meshState string) string {
	return cache.HashKey(meshState)
}

// Route registration.

func RegisterAIRoutes(mux *http.ServeMux, cfg *llm.Config, queue *llm.LLMQueue, summaryCache *SummaryCache, quizCache *QuizCache) {

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
			if err := llm.ValidateBaseURL(body.BaseURL); err != nil {
				writeJSON(w, http.StatusBadRequest, map[string]string{"error": fmt.Sprintf("invalid base URL: %v", err)})
				return
			}
		}
		cfg.Update(body.BaseURL, body.Model, body.APIKey)
		platform.Logf("LLM config updated: base_url=%s model=%s has_key=%v", body.BaseURL, body.Model, body.APIKey != "")
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

		sfKey := cache.HashKey("narrate", userPrompt)
		content, err := queue.Call(r.Context(), sfKey, []llm.ChatMessage{
			{Role: "system", Content: narratorSystemPrompt},
			{Role: "user", Content: userPrompt},
		}, 200, "narrate")

		if err != nil {
			if errors.Is(err, llm.ErrQueueFull) {
				w.Header().Set("Retry-After", "10")
				writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "server busy, try again shortly"})
				return
			}
			platform.Logf("narrate LLM error: %v", err)
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

		messages := []llm.ChatMessage{
			{Role: "system", Content: whatIfSystemPrompt},
			{Role: "user", Content: "Current mesh state:\n" + req.MeshState},
			{Role: "assistant", Content: "I can see your mesh network state. What would you like to explore?"},
		}

		for _, h := range req.History {
			messages = append(messages, llm.ChatMessage{Role: "user", Content: h.Question})
			messages = append(messages, llm.ChatMessage{Role: "assistant", Content: h.Answer})
		}

		messages = append(messages, llm.ChatMessage{Role: "user", Content: req.Question})

		sfKey := cache.HashKey("what-if", req.Question, req.MeshState)
		content, err := queue.Call(r.Context(), sfKey, messages, 400, "what-if")
		if err != nil {
			if errors.Is(err, llm.ErrQueueFull) {
				w.Header().Set("Retry-After", "10")
				writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "server busy, try again shortly"})
				return
			}
			platform.Logf("what-if LLM error: %v", err)
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
		cacheKey := SummaryCacheKey(req)
		if cached, ok := summaryCache.Get(cacheKey); ok {
			platform.Logf("summary cache hit")
			writeJSON(w, http.StatusOK, cached)
			return
		}

		userContent := "Here's my session data:\n" + req.MeshState
		if req.QuizScore != nil && req.QuizTotal != nil {
			userContent += fmt.Sprintf("\n\nQuiz results: %d/%d correct", *req.QuizScore, *req.QuizTotal)
		}

		content, err := queue.Call(r.Context(), cacheKey, []llm.ChatMessage{
			{Role: "system", Content: summarySystemPrompt},
			{Role: "user", Content: userContent},
		}, 600, "summary")

		if err != nil {
			if errors.Is(err, llm.ErrQueueFull) {
				w.Header().Set("Retry-After", "10")
				writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "server busy, try again shortly"})
				return
			}
			platform.Logf("summary LLM error: %v", err)
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}

		resp := SummaryResponse{Summary: content}
		summaryCache.Set(cacheKey, resp)
		writeJSON(w, http.StatusOK, resp)
	})

	// POST /ai/quiz — LLM-generated session-aware quiz, static fallback
	mux.HandleFunc("POST /ai/quiz", func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
		var req QuizRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}

		// Check quiz cache first
		qCacheKey := QuizCacheKey(req.MeshState)
		if cached, ok := quizCache.Get(qCacheKey); ok {
			platform.Logf("quiz cache hit (%d questions, source=%s)", len(cached.Questions), cached.Source)
			writeJSON(w, http.StatusOK, cached)
			return
		}

		// Try LLM first
		userPrompt := "Here is the student's current mesh network session data:\n" + req.MeshState
		content, err := queue.Call(r.Context(), qCacheKey, []llm.ChatMessage{
			{Role: "system", Content: quizSystemPrompt},
			{Role: "user", Content: userPrompt},
		}, 2000, "quiz")

		if err == nil {
			var questions []quiz.Question
			if parseErr := json.Unmarshal([]byte(content), &questions); parseErr == nil {
				valid := make([]quiz.Question, 0, len(questions))
				for _, q := range questions {
					if len(q.Options) == 4 && q.Correct >= 0 && q.Correct < 4 && q.Text != "" {
						valid = append(valid, q)
					}
				}
				if len(valid) >= 5 {
					platform.Logf("quiz: LLM generated %d valid questions", len(valid))
					qResp := QuizResponse{Questions: valid, Source: "ai"}
					quizCache.Set(qCacheKey, qResp)
					writeJSON(w, http.StatusOK, qResp)
					return
				}
				platform.Logf("quiz: LLM produced only %d valid questions, falling back to static", len(valid))
			} else {
				platform.Logf("quiz: LLM parse error: %v", parseErr)
			}
		} else {
			if errors.Is(err, llm.ErrQueueFull) {
				w.Header().Set("Retry-After", "10")
				writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "server busy, try again shortly"})
				return
			}
			platform.Logf("quiz: LLM error: %v, falling back to static", err)
		}

		// Fallback: static question pool
		picked := quiz.PickStaticQuestions(10)
		writeJSON(w, http.StatusOK, QuizResponse{Questions: picked, Source: "static"})
	})

	// POST /ai/test
	mux.HandleFunc("POST /ai/test", func(w http.ResponseWriter, r *http.Request) {
		sfKey := cache.HashKey("test")
		content, err := queue.Call(r.Context(), sfKey, []llm.ChatMessage{
			{Role: "user", Content: "Say 'OK' if you can hear me."},
		}, 10, "test")
		if err != nil {
			if errors.Is(err, llm.ErrQueueFull) {
				w.Header().Set("Retry-After", "10")
				writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "server busy, try again shortly"})
				return
			}
			writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"response": content})
	})
}
