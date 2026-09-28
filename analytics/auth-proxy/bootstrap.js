// Entry point for the distroless runtime image, which has no shell to evaluate
// the "${DEBUG_ENABLED:+--inspect=...}" expansion used by the local "npm start" script.
// Opens the same inspector port via the Node API when DEBUG_ENABLED is set.
if (process.env.DEBUG_ENABLED) {
  require('inspector').open(9229, '0.0.0.0');
}

require('./server.js');
