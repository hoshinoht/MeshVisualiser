package main

import (
	"log"
	"net/http"
	"os"
	"time"
)

func main() {
	store := NewStore()
	llmCfg := NewLLMConfig()
	summaryCache := NewSummaryCache(1 * time.Hour)

	// Expire rooms after 1 hour of inactivity.
	go func() {
		for range time.Tick(10 * time.Minute) {
			if n := store.Cleanup(1 * time.Hour); n > 0 {
				log.Printf("Cleaned up %d expired rooms", n)
			}
		}
	}()

	mux := http.NewServeMux()
	registerRoomRoutes(mux, store)
	registerAIRoutes(mux, llmCfg, summaryCache)

	// Middleware chain: cors → apiKey → log → mux
	var handler http.Handler = logMiddleware(mux)
	if apiKey := os.Getenv("MESH_API_KEY"); apiKey != "" {
		log.Println("API key authentication enabled")
		handler = apiKeyMiddleware(apiKey, handler)
	}
	handler = corsMiddleware(handler)

	addr := ":8080"
	log.Printf("Mesh server starting on %s", addr)
	log.Fatal(http.ListenAndServe(addr, handler))
}
