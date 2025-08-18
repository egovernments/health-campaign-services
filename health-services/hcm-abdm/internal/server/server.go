package server

import (
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"

	"digit-abdm/internal/core/ports"
	"digit-abdm/internal/handlers"
)

// Server represents the server that can handle both REST and gRPC requests
type Server struct {
	restPort    int
	grpcPort    int
	service     ports.MessageService
	abdmservice ports.ABHAService
	restSrv     *http.Server
	// grpcSrv  *grpc.Server
}

// NewServer creates a new server instance
func NewServer(restPort, grpcPort int, service ports.MessageService) *Server {
	return &Server{
		restPort: restPort,
		grpcPort: grpcPort,
		service:  service,
	}
}

// Start starts both REST and gRPC servers
func (s *Server) Start() error {
	// Start REST server
	go func() {
		router := gin.Default()
		apiGroup := router.Group("/api")

		// Initialize REST handler
		restHandler := handlers.NewMessageHandler(s.service)
		restHandler.RegisterRoutes(apiGroup)

		s.restSrv = &http.Server{
			Addr:    fmt.Sprintf(":%d", s.restPort),
			Handler: router,
		}

		if err := s.restSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			fmt.Printf("REST server error: %v\n", err)
		}
	}()

	// // Start gRPC server
	// go func() {
	// 	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", s.grpcPort))
	// 	if err != nil {
	// 		fmt.Printf("Failed to listen for gRPC: %v\n", err)
	// 		return
	// 	}

	// 	s.grpcSrv = grpc.NewServer()

	// 	// Initialize gRPC handler
	// 	grpcHandler := handlers.NewGRPCServer(s.service)
	// 	localizationv1.RegisterLocalizationServiceServer(s.grpcSrv, grpcHandler)
	// 	// Enable reflection
	// 	reflection.Register(s.grpcSrv)

	// 	if err := s.grpcSrv.Serve(lis); err != nil {
	// 		fmt.Printf("gRPC server error: %v\n", err)
	// 	}
	// }()

	return nil
}

// Stop gracefully stops both REST and gRPC servers
func (s *Server) Stop() error {
	if s.restSrv != nil {
		if err := s.restSrv.Close(); err != nil {
			return fmt.Errorf("error closing REST server: %v", err)
		}
	}

	if s.grpcSrv != nil {
		s.grpcSrv.GracefulStop()
	}

	return nil
}
