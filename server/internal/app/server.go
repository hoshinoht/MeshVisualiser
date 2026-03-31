package app

import (
	"net/http"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/httpapi"
	"github.com/inf2007/inf2007-team07-2026/server/internal/llm"
	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
	"github.com/inf2007/inf2007-team07-2026/server/internal/room"
)

// NewHandler wires the application dependencies and returns the HTTP handler.
func NewHandler(apiKey string) http.Handler {
	store := room.NewStore()
	llmCfg := llm.NewConfig()
	llmQueue := llm.NewQueue(llmCfg, 1, 3, 120*time.Second)

	stop := make(chan struct{})
	summaryCache := httpapi.NewSummaryCache(1*time.Hour, stop)
	quizCache := httpapi.NewQuizCache(30*time.Minute, stop)

	// Expire rooms after 1 hour of inactivity.
	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			if n := store.Cleanup(1 * time.Hour); n > 0 {
				platform.Logf("Cleaned up %d expired rooms", n)
			}
		}
	}()

	mux := http.NewServeMux()
	httpapi.RegisterRoomRoutes(mux, store)
	httpapi.RegisterAIRoutes(mux, llmCfg, llmQueue, summaryCache, quizCache)

	// Middleware chain: cors → security headers → apiKey → log → mux
	var handler http.Handler = httpapi.LogMiddleware(mux)
	if apiKey != "" {
		platform.Logf("API key authentication enabled")
		handler = httpapi.APIKeyMiddleware(apiKey, handler)
	}
	handler = httpapi.SecurityHeadersMiddleware(handler)
	handler = httpapi.CORSMiddleware(handler)

	return handler
}
