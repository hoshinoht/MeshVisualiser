package httpapi

import (
	"encoding/json"
	"net/http"

	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
)

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		platform.Logf("writeJSON encode error: %v", err)
	}
}
