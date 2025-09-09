// package errorsx

// import "github.com/gin-gonic/gin"

// func Recovery() gin.HandlerFunc {
// 	return func(c *gin.Context) {
// 		defer func() {
// 			if rec := recover(); rec != nil {
// 				// Only write if nothing has been written yet
// 				if !c.Writer.Written() {
// 					Write(c, Internal(CodePanic, "panic recovered", map[string]interface{}{"panic": rec}, nil))
// 				}
// 				c.Abort()
// 				return
// 			}
// 			// If any handler called c.Error(err), write exactly once.
// 			if len(c.Errors) > 0 {
// 				if !c.Writer.Written() {
// 					Write(c, c.Errors.Last().Err)
// 				}
// 				c.Abort()
// 			}
// 		}()
// 		c.Next()
// 	}
// }

package errorsx

import "github.com/gin-gonic/gin"

func Recovery() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if rec := recover(); rec != nil {
				if !c.Writer.Written() {
					Write(c, Internal(CodePanic, "panic recovered", map[string]interface{}{"panic": rec}, nil))
				}
				c.Abort()
				return
			}
			if len(c.Errors) > 0 {
				if !c.Writer.Written() {
					Write(c, c.Errors.Last().Err)
				}
				c.Abort()
			}
		}()
		c.Next()
	}
}
