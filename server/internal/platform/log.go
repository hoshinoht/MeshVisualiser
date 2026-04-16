package platform

import (
	"log"
	"time"
)

// SGT is the fixed Singapore timezone (UTC+8) used for logs.
var SGT = time.FixedZone("SGT", 8*60*60)

func init() {
	// Suppress default log timestamp — we prepend SGT timestamps ourselves.
	log.SetFlags(0)
}

// Logf prints a timestamped log line in Singapore time.
func Logf(format string, args ...any) {
	ts := time.Now().In(SGT).Format("2006/01/02 15:04:05")
	log.Printf(ts+" "+format, args...)
}
