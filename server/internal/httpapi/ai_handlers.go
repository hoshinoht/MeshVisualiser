package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/cache"
	"github.com/inf2007/inf2007-team07-2026/server/internal/llm"
	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
	"github.com/inf2007/inf2007-team07-2026/server/internal/quiz"
)

// System prompts.

const narratorSystemPrompt = `You are a networking protocol tutor embedded in a mesh networking education app.
You will receive the current mesh state and a short list of recent protocol events.

Write one short overlay explanation for the student.

Rules:
- Plain text only. No markdown.
- 1-2 sentences only.
- Maximum 55 words.
- Explain what happened, why it happened, and why it matters.
- Use only facts from the input.
- Mention at most two concrete details from the input.
- Do not invent peer names, RTT values, packet counts, or causes.
- If the exact cause is uncertain, give the most likely explanation briefly.`

const whatIfSystemPrompt = `You are a networking lab assistant. The student has a live mesh network and wants to explore
"what if" scenarios.

Answer the student's question using only the supplied mesh state and conversation history.

Rules:
- Plain text only.
- Maximum 120 words.
- 2-4 short paragraphs.
- First explain what would likely happen.
- Then explain why, using the current mesh state.
- If useful, end with: "Try this: ...".
- Use real peer names and metrics only if they are present in the input.
- If the answer cannot be inferred from the given state, say what is unknown instead of guessing.`

const summarySystemPrompt = `You are a networking education tutor reviewing a student's mesh networking lab session.
Write a concise personalized summary using only the supplied session data.

Format exactly:

## Session
- ...
## Concepts
- ...
- ...
## Next
- ...
- ...

Rules:
- Markdown headings and bullet points only.
- 160-220 words total.
- Mention only concepts supported by the session data.
- Use concrete session facts where available.
- Do not repeat the same metric more than once.
- Do not invent events or outcomes.`

const quizSystemPrompt = `You are a networking quiz generator for a mesh networking education app.
Generate valid JSON only.
Do not use markdown.
Do not use code fences.
Do not add any text before or after the JSON.

Return exactly this object shape:
{"questions":[...]}

Generate exactly 10 multiple-choice questions.

Each question object must be:
{"text":"question text","options":["A","B","C","D"],"correct":0,"category":"SESSION|CONCEPT|SCENARIO","explanation":"Why the answer is correct"}

Rules:
- Exactly 4 options per question.
- Exactly 1 correct answer.
- "correct" must be 0, 1, 2, or 3.
- Aim for 3-4 SESSION, 4-5 CONCEPT, and 2-3 SCENARIO questions when the input supports it.
- Use SESSION questions only when the needed facts are clearly present in the input.
- If data is sparse, prefer CONCEPT and SCENARIO questions instead of inventing details.
- Use only facts present in the input.
- Do not invent peer names, RTT values, loss values, leader identity, or topology.
- Keep each explanation to 1-2 short sentences.`

const (
	narratorTemperature = 0.45
	whatIfTemperature   = 0.65
	summaryTemperature  = 0.55
	quizTemperature     = 0.15
	testTemperature     = 0.0
)

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
		content, err := queue.CallWithOptions(r.Context(), sfKey, []llm.ChatMessage{
			{Role: "system", Content: narratorSystemPrompt},
			{Role: "user", Content: userPrompt},
		}, llm.GenerationOptions{MaxTokens: 200, Temperature: narratorTemperature, Caller: "narrate"})

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
		content, err := queue.CallWithOptions(r.Context(), sfKey, messages, llm.GenerationOptions{MaxTokens: 400, Temperature: whatIfTemperature, Caller: "what-if"})
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

		content, err := queue.CallWithOptions(r.Context(), cacheKey, []llm.ChatMessage{
			{Role: "system", Content: summarySystemPrompt},
			{Role: "user", Content: userContent},
		}, llm.GenerationOptions{MaxTokens: 600, Temperature: summaryTemperature, Caller: "summary"})

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
		content, err := queue.CallWithOptions(r.Context(), qCacheKey, []llm.ChatMessage{
			{Role: "system", Content: quizSystemPrompt},
			{Role: "user", Content: userPrompt},
		}, llm.GenerationOptions{MaxTokens: 2000, Temperature: quizTemperature, Caller: "quiz"})

		if err == nil {
			var questions []quiz.Question
			trimmed := strings.TrimSpace(content)
			if parseErr := json.Unmarshal([]byte(trimmed), &questions); parseErr == nil {
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
				var wrapped struct {
					Questions []quiz.Question `json:"questions"`
				}
				if wrappedErr := json.Unmarshal([]byte(trimmed), &wrapped); wrappedErr == nil {
					valid := make([]quiz.Question, 0, len(wrapped.Questions))
					for _, q := range wrapped.Questions {
						if len(q.Options) == 4 && q.Correct >= 0 && q.Correct < 4 && q.Text != "" {
							valid = append(valid, q)
						}
					}
					if len(valid) >= 5 {
						platform.Logf("quiz: LLM generated %d valid wrapped questions", len(valid))
						qResp := QuizResponse{Questions: valid, Source: "ai"}
						quizCache.Set(qCacheKey, qResp)
						writeJSON(w, http.StatusOK, qResp)
						return
					}
					platform.Logf("quiz: LLM produced only %d valid wrapped questions, falling back to static", len(valid))
				} else {
					platform.Logf("quiz: LLM parse error: %v / wrapped parse error: %v", parseErr, wrappedErr)
				}
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
		content, err := queue.CallWithOptions(r.Context(), sfKey, []llm.ChatMessage{
			{Role: "user", Content: "Say 'OK' if you can hear me."},
		}, llm.GenerationOptions{MaxTokens: 10, Temperature: testTemperature, Caller: "test"})
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
