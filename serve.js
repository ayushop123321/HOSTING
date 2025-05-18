const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const WEBSITE_DIR = path.join(__dirname, 'Website');

const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'text/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.webp': 'image/webp',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf',
    '.eot': 'application/vnd.ms-fontobject',
    '.otf': 'font/otf',
};

const server = http.createServer((req, res) => {
    console.log(`${req.method} ${req.url}`);
    
    // Parse URL
    let url = req.url;
    
    // Handle root URL
    if (url === '/') {
        url = '/index.html';
    }
    
    // Construct file path
    const filePath = path.join(WEBSITE_DIR, url);
    
    // Get file extension
    const extname = String(path.extname(filePath)).toLowerCase();
    
    // Set content type based on file extension
    const contentType = MIME_TYPES[extname] || 'application/octet-stream';
    
    // Read file
    fs.readFile(filePath, (error, content) => {
        if (error) {
            if (error.code === 'ENOENT') {
                // File not found
                console.log(`File not found: ${filePath}`);
                
                // Try to serve 404.html if it exists
                const notFoundPath = path.join(WEBSITE_DIR, '404.html');
                fs.readFile(notFoundPath, (err, content) => {
                    if (err) {
                        // No 404 page found, send simple text response
                        res.writeHead(404, { 'Content-Type': 'text/html' });
                        res.end('<h1>404 Not Found</h1><p>The page you requested was not found.</p>');
                    } else {
                        // Serve 404 page
                        res.writeHead(404, { 'Content-Type': 'text/html' });
                        res.end(content);
                    }
                });
            } else {
                // Server error
                console.error(`Server error: ${error.code}`);
                res.writeHead(500, { 'Content-Type': 'text/html' });
                res.end('<h1>500 Internal Server Error</h1><p>Sorry, there was a problem processing your request.</p>');
            }
        } else {
            // Success - serve the file
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
});

server.listen(PORT, () => {
    console.log(`Server running at http://localhost:${PORT}/`);
    console.log(`Test announcements page: http://localhost:${PORT}/test-announcements.html`);
}); 