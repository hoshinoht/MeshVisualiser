package httpapi

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

var dummyHandler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
})

func TestMiddleware_CORS_AllowedOrigin(t *testing.T) {
	const origin = "https://test.example.com"
	allowedOrigins[origin] = true
	defer delete(allowedOrigins, origin)

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Origin", origin)
	rec := httptest.NewRecorder()

	CORSMiddleware(dummyHandler).ServeHTTP(rec, req)

	if got := rec.Header().Get("Access-Control-Allow-Origin"); got != origin {
		t.Fatalf("Access-Control-Allow-Origin = %q, want %q", got, origin)
	}
}

func TestMiddleware_CORS_UnknownOrigin(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Origin", "https://evil.com")
	rec := httptest.NewRecorder()

	CORSMiddleware(dummyHandler).ServeHTTP(rec, req)

	if got := rec.Header().Get("Access-Control-Allow-Origin"); got != "" {
		t.Fatalf("Access-Control-Allow-Origin = %q, want empty", got)
	}
	if got := rec.Body.String(); got != "ok" {
		t.Fatalf("body = %q, want %q", got, "ok")
	}
}

func TestMiddleware_CORS_Preflight(t *testing.T) {
	req := httptest.NewRequest(http.MethodOptions, "/", nil)
	req.Header.Set("Origin", "https://anything.example")
	rec := httptest.NewRecorder()

	CORSMiddleware(dummyHandler).ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusNoContent)
	}
}

func TestMiddleware_APIKey_Valid(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/rooms", nil)
	req.Header.Set("X-API-Key", "secret123")
	rec := httptest.NewRecorder()

	APIKeyMiddleware("secret123", dummyHandler).ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
}

func TestMiddleware_APIKey_Invalid(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/rooms", nil)
	req.Header.Set("X-API-Key", "wrong")
	rec := httptest.NewRecorder()

	APIKeyMiddleware("secret123", dummyHandler).ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("failed to decode body: %v", err)
	}
	if !bytes.Contains([]byte(body["error"]), []byte("unauthorized")) {
		t.Fatalf("error body = %q, want to contain %q", body["error"], "unauthorized")
	}
}

func TestMiddleware_APIKey_HealthExempt(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()

	APIKeyMiddleware("secret123", dummyHandler).ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
}

func TestMiddleware_SecurityHeaders(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()

	SecurityHeadersMiddleware(dummyHandler).ServeHTTP(rec, req)

	if got := rec.Header().Get("X-Content-Type-Options"); got != "nosniff" {
		t.Fatalf("X-Content-Type-Options = %q, want %q", got, "nosniff")
	}
	if got := rec.Header().Get("X-Frame-Options"); got != "DENY" {
		t.Fatalf("X-Frame-Options = %q, want %q", got, "DENY")
	}
}
