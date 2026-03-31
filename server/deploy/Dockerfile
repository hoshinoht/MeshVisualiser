FROM golang:1.25-alpine AS build
WORKDIR /app
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o mesh-server ./cmd/mesh-server

FROM alpine:3.20
COPY --from=build /app/mesh-server /mesh-server
EXPOSE 8080
ENTRYPOINT ["/mesh-server"]
