package main

import (
	"log"
	"net/http"
	"os"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/app"
	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
)

func main() {
	handler := app.NewHandler(os.Getenv("MESH_API_KEY"))

	addr := ":8080"
	platform.Logf("Mesh server starting on %s", addr)

	srv := &http.Server{
		Addr:         addr,
		Handler:      handler,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 130 * time.Second,
		IdleTimeout:  60 * time.Second,
	}
	log.Fatal(srv.ListenAndServe())
}
