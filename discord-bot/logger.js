// Simple logging utility
const logger = {
  info: (message) => {
    console.log(`[INFO] ${message}`);
  },
  error: (message) => {
    console.error(`[ERROR] ${message}`);
  },
  warn: (message) => {
    console.warn(`[WARN] ${message}`);
  },
  debug: (message) => {
    if (process.env.DEBUG === 'true') {
      console.log(`[DEBUG] ${message}`);
    }
  }
};

module.exports = logger; 