//package main
//
//import (
//	"context"
//	"database/sql"
//	"fmt"
//	"log"
//	"net/http"
//	"os"
//	"os/signal"
//	"syscall"
//	"time"
//
//	"digit-abdm/configs"
//	"digit-abdm/internal/core/services"
//	"digit-abdm/internal/handlers"
//	dbpostgres "digit-abdm/internal/repositories/postgres"
//	"github.com/gin-gonic/gin"
//	"github.com/golang-migrate/migrate/v4"
//	"github.com/golang-migrate/migrate/v4/database/postgres"
//	_ "github.com/golang-migrate/migrate/v4/source/file"
//	_ "github.com/lib/pq"
//)
//
//func main() {
//	// Load application configurations
//	config := configs.LoadConfig()
//
//	// Setup database connection
//	db, err := sql.Open("postgres", fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
//		config.DBHost, config.DBPort, config.DBUser, config.DBPassword, config.DBName))
//	if err != nil {
//		log.Fatalf("could not connect to the database: %v", err)
//	}
//
//	// Apply database migrations
//	if config.EnableMigrations {
//		driver, err := postgres.WithInstance(db, &postgres.Config{})
//		if err != nil {
//			log.Fatalf("could not create migrate driver: %v", err)
//		}
//
//		m, err := migrate.NewWithDatabaseInstance("file://migrations", "postgres", driver)
//		if err != nil {
//			log.Fatalf("could not create migrate instance: %v", err)
//		}
//
//		if err := m.Up(); err != nil && err != migrate.ErrNoChange {
//			log.Fatalf("failed to apply migrations: %v", err)
//		}
//
//		log.Println("Database migrations applied successfully")
//	} else {
//		log.Println("Skipping migrations as 'EnableMigrations' is set to false")
//	}
//
//	abhaRepo := dbpostgres.NewAbhaRepository(db)
//	abhaService := services.NewABHAService(config, abhaRepo)
//
//	// Setup HTTP server
//	httpRouter := gin.Default()
//	// messageHandler := handlers.NewMessageHandler(messageService)
//	apiGroup := httpRouter.Group("/api")
//	// messageHandler.RegisterRoutes(apiGroup)
//
//	// Register ABHA routes
//	abhaHandler := handlers.NewABHAHandler(abhaService, config)
//	abhaHandler.RegisterRoutes(apiGroup)
//
//	httpServer := &http.Server{
//		Addr:    fmt.Sprintf(":%d", config.RESTPort),
//		Handler: httpRouter,
//	}
//	// Start servers
//	go func() {
//		log.Printf("HTTP server listening on :%d", config.RESTPort)
//		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
//			log.Fatalf("HTTP server failed: %v", err)
//		}
//	}()
//
//	// Graceful shutdown
//	quit := make(chan os.Signal, 1)
//	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
//	<-quit
//	log.Println("Shutting down servers...")
//
//	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
//	defer cancel()
//
//	if err := httpServer.Shutdown(ctx); err != nil {
//		log.Fatalf("HTTP server shutdown failed: %v", err)
//	}
//	// grpcServer.GracefulStop()
//	log.Println("Servers gracefully stopped")
//}

//-------------------------------------------------------------

// package main

// import (
// 	"context"
// 	"database/sql"
// 	"fmt"
// 	"log"
// 	"net/http"
// 	"os"
// 	"os/signal"
// 	"syscall"
// 	"time"

// 	"digit-abdm/configs"
// 	"digit-abdm/internal/core/services"
// 	"digit-abdm/internal/handlers"
// 	dbpostgres "digit-abdm/internal/repositories/postgres"

// 	"github.com/gin-gonic/gin"
// 	_ "github.com/lib/pq"
// )

// func main() {
// 	// Load application configurations
// 	cfg := configs.LoadConfig()

// 	// --------------------------
// 	// Database connection
// 	// --------------------------
// 	// NOTE: sslmode is now taken from env (DB_SSL_MODE), e.g. "disable", "require"
// 	dsn := fmt.Sprintf(
// 		"host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
// 		cfg.DBHost, cfg.DBPort, cfg.DBUser, cfg.DBPassword, cfg.DBName, cfg.DBSSLMode,
// 	)

// 	db, err := sql.Open("postgres", dsn)
// 	if err != nil {
// 		log.Fatalf("could not open DB connection: %v", err)
// 	}
// 	// Reasonable defaults; tune as needed
// 	db.SetMaxOpenConns(10)
// 	db.SetMaxIdleConns(5)
// 	db.SetConnMaxLifetime(30 * time.Minute)

// 	// Ping with timeout to fail fast if DB is unreachable/policy mismatch (e.g., SSL)
// 	{
// 		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
// 		defer cancel()
// 		if err := db.PingContext(ctx); err != nil {
// 			log.Fatalf("database ping failed: %v", err)
// 		}
// 	}

// 	// --------------------------
// 	// Migrations
// 	// --------------------------
// 	// IMPORTANT: Migrations are handled by a separate initContainer/image now.
// 	// Do NOT run migrations here. This keeps app startup independent and robust.

// 	// --------------------------
// 	// Wire up services/handlers
// 	// --------------------------
// 	abhaRepo := dbpostgres.NewAbhaRepository(db)
// 	abhaService := services.NewABHAService(cfg, abhaRepo)

// 	router := gin.Default()

// 	// Lightweight health endpoint (useful even if probes are disabled)
// 	router.GET("/health", func(c *gin.Context) {
// 		c.String(http.StatusOK, "ok")
// 	})

// 	api := router.Group("/api")
// 	abhaHandler := handlers.NewABHAHandler(abhaService, cfg)
// 	abhaHandler.RegisterRoutes(api)

// 	// --------------------------
// 	// HTTP server & graceful shutdown
// 	// --------------------------
// 	httpServer := &http.Server{
// 		Addr:    fmt.Sprintf(":%d", cfg.RESTPort),
// 		Handler: router,
// 	}

// 	go func() {
// 		log.Printf("HTTP server listening on :%d", cfg.RESTPort)
// 		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
// 			log.Fatalf("HTTP server failed: %v", err)
// 		}
// 	}()

// 	// Wait for SIGINT/SIGTERM
// 	quit := make(chan os.Signal, 1)
// 	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
// 	<-quit
// 	log.Println("Shutting down servers...")

// 	// Graceful shutdown with timeout
// 	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
// 	defer cancel()

// 	if err := httpServer.Shutdown(ctx); err != nil {
// 		log.Printf("HTTP server shutdown error: %v", err)
// 	}

// 	// Close DB
// 	if err := db.Close(); err != nil {
// 		log.Printf("DB close error: %v", err)
// 	}

// 	log.Println("Servers gracefully stopped")
// }

package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"digit-abdm/configs"
	"digit-abdm/internal/core/services"
	errorsx "digit-abdm/internal/errorsx"
	"digit-abdm/internal/handlers"
	dbpostgres "digit-abdm/internal/repositories/postgres"

	"github.com/gin-gonic/gin"
	_ "github.com/lib/pq"
)

func main() {
	// Load application configurations
	cfg := configs.LoadConfig()

	// --------------------------
	// Database connection
	// --------------------------
	dsn := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
		cfg.DBHost, cfg.DBPort, cfg.DBUser, cfg.DBPassword, cfg.DBName, cfg.DBSSLMode,
	)

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		log.Fatalf("could not open DB connection: %v", err)
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)

	{
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := db.PingContext(ctx); err != nil {
			log.Fatalf("database ping failed: %v", err)
		}
	}

	// --------------------------
	// Wire up services/handlers
	// --------------------------
	abhaRepo := dbpostgres.NewAbhaRepository(db)
	abhaService := services.NewABHAService(cfg, abhaRepo)

	// router := gin.Default()
	router := gin.New()
	router.Use(gin.Logger())
	router.Use(errorsx.Recovery())

	// Base context group (e.g., /hcm-abha)
	base := router.Group(cfg.ContextPath)

	// Health under context path
	base.GET("/health", func(c *gin.Context) {
		c.String(http.StatusOK, "ok")
	})

	// API under context path -> /hcm-abha/api/...
	api := base.Group("/api")

	abhaHandler := handlers.NewABHAHandler(abhaService, cfg)
	abhaHandler.RegisterRoutes(api)

	// --------------------------
	// HTTP server & graceful shutdown
	// --------------------------
	httpServer := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.RESTPort),
		Handler: router,
	}

	go func() {
		log.Printf("HTTP server listening on :%d (contextPath=%s)", cfg.RESTPort, cfg.ContextPath)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server failed: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down servers...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := httpServer.Shutdown(ctx); err != nil {
		log.Printf("HTTP server shutdown error: %v", err)
	}

	if err := db.Close(); err != nil {
		log.Printf("DB close error: %v", err)
	}

	log.Println("Servers gracefully stopped")
}
