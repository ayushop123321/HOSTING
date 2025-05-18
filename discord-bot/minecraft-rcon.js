// Minecraft RCON Connection Utility
const Rcon = require('rcon');
const { promisify } = require('util');
const logger = require('./logger');
require('dotenv').config();

// Environment variables
const MINECRAFT_SERVER_IP = process.env.MINECRAFT_SERVER_IP || 'localhost';
const MINECRAFT_SERVER_PORT = parseInt(process.env.MINECRAFT_SERVER_PORT || '25575');
const MINECRAFT_RCON_PASSWORD = process.env.MINECRAFT_RCON_PASSWORD || '';

// Connection pool
let connectionPool = null;
let lastUsed = Date.now();
const CONN_TIMEOUT = 30000; // 30 seconds

/**
 * Connect to a Minecraft server using RCON
 * @param {string} host - The server host
 * @param {number} port - The RCON port
 * @param {string} password - The RCON password
 * @returns {Promise<object>} - The RCON connection
 */
async function connectRCON(host = MINECRAFT_SERVER_IP, port = MINECRAFT_SERVER_PORT, password = MINECRAFT_RCON_PASSWORD) {
    return new Promise((resolve, reject) => {
        try {
            console.log(`Connecting to RCON at ${host}:${port}...`);
            const conn = new Rcon(
                host,
                port,
                password,
                { tcp: true, challenge: false }
            );
            
            conn.on('auth', () => {
                console.log('RCON: Authentication successful');
                resolve(conn);
            });
            
            conn.on('error', (error) => {
                console.error(`RCON: Connection error: ${error.message}`);
                reject(error);
            });
            
            conn.on('end', () => {
                console.log('RCON: Connection closed');
            });
            
            conn.connect();
        } catch (error) {
            console.error(`RCON: Error creating connection: ${error.message}`);
            reject(error);
        }
    });
}

/**
 * Execute a Minecraft command via RCON connection
 * @param {object} conn - The RCON connection
 * @param {string} command - The command to execute
 * @returns {Promise<string>} - The command response
 */
async function executeMinecraftCommand(conn, command) {
    return new Promise((resolve, reject) => {
        try {
            console.log(`Executing Minecraft command: ${command}`);
            
            conn.send(command, (response) => {
                console.log(`RCON response: ${response}`);
                resolve(response);
            });
        } catch (error) {
            console.error(`Failed to execute Minecraft command: ${error.message}`);
            reject(error);
        }
    });
}

/**
 * Get an active RCON connection, reusing if possible
 */
async function getRconConnection() {
    // If we have a connection and it's not too old, reuse it
    if (connectionPool && connectionPool.connected && (Date.now() - lastUsed < CONN_TIMEOUT)) {
        lastUsed = Date.now();
        return connectionPool;
    }
    
    // Close old connection if exists
    if (connectionPool && connectionPool.connected) {
        try {
            connectionPool.disconnect();
        } catch (error) {
            logger.error(`Error disconnecting from RCON: ${error.message}`);
        }
    }
    
    // Create new connection
    return new Promise((resolve, reject) => {
        try {
            const conn = new Rcon(
                MINECRAFT_SERVER_IP,
                MINECRAFT_SERVER_PORT,
                MINECRAFT_RCON_PASSWORD,
                { tcp: true, challenge: false }
            );
            
            conn.on('auth', () => {
                logger.info('RCON: Authentication successful');
                connectionPool = conn;
                lastUsed = Date.now();
                
                // Keep connection alive with periodic pings
                if (global.rconPingInterval) {
                    clearInterval(global.rconPingInterval);
                }
                
                global.rconPingInterval = setInterval(() => {
                    if (connectionPool && connectionPool.connected && (Date.now() - lastUsed > 60000)) {
                        // Only ping if no activity for 1 minute
                        connectionPool.send('ping');
                        lastUsed = Date.now();
                    }
                }, 60000); // Check every minute
                
                resolve(conn);
            });
            
            conn.on('error', (error) => {
                logger.error(`RCON: Connection error: ${error.message}`);
                reject(error);
            });
            
            conn.on('end', () => {
                logger.info('RCON: Connection closed');
                connectionPool = null;
            });
            
            conn.connect();
        } catch (error) {
            logger.error(`RCON: Error creating connection: ${error.message}`);
            reject(error);
        }
    });
}

/**
 * Check if a player is online on the server
 * @param {object} conn - The RCON connection
 * @param {string} username - The player's username
 * @returns {Promise<boolean>} - True if the player is online
 */
async function isPlayerOnline(conn, username) {
    try {
        const response = await executeMinecraftCommand(conn, `list`);
        return response.toLowerCase().includes(username.toLowerCase());
    } catch (error) {
        console.error(`Error checking if player is online: ${error.message}`);
        return false;
    }
}

/**
 * Apply a rank to a player using LuckPerms
 * @param {object} conn - The RCON connection
 * @param {string} username - The player's username
 * @param {string} rank - The rank to apply
 * @returns {Promise<boolean>} - True if successful
 */
async function applyRank(conn, username, rank) {
    try {
        // Convert rank name to proper format (e.g., vip_plus -> vip+)
        let formattedRank = rank;
        if (rank === 'vip_plus') formattedRank = 'vip+';
        
        // Execute LuckPerms command to set the rank
        const response = await executeMinecraftCommand(
            conn,
            `lp user ${username} parent set ${formattedRank}`
        );
        
        // Check if the command was successful
        return !response.toLowerCase().includes('error') && 
               !response.toLowerCase().includes('failed');
    } catch (error) {
        console.error(`Error applying rank: ${error.message}`);
        return false;
    }
}

/**
 * Execute a general Minecraft command
 * @param {string} command - The command to execute
 * @returns {Promise<string>} - The command response
 */
async function executeCommand(command) {
    try {
        logger.info(`Executing Minecraft command: ${command}`);
        
        // Add auth token to secure commands if configured
        const authToken = process.env.RCON_AUTH_TOKEN;
        if (authToken && 
            (command.startsWith('giverank') || 
             command.startsWith('givecoin') || 
             command.startsWith('is_player_exists'))) {
            command = `${command} auth_token ${authToken}`;
        }
        
        const conn = await getRconConnection();
        const sendAsync = promisify(conn.send).bind(conn);
        const response = await sendAsync(command);
        
        // Parse common error responses
        if (response.includes('ERROR:')) {
            logger.error(`RCON command error: ${response}`);
            // Extract error message
            const errorParts = response.split(':');
            if (errorParts.length > 1) {
                throw new Error(errorParts.slice(1).join(':').replace(/_/g, ' '));
            }
            throw new Error(response);
        }
        
        return response;
    } catch (error) {
        logger.error(`Failed to execute Minecraft command: ${error.message}`);
        throw error;
    }
}

/**
 * Check if a player exists on the server
 * @param {string} username - The player's username
 * @returns {Promise<object>} - Object with exists and online properties
 */
async function isPlayerExists(username) {
    try {
        const response = await executeCommand(`is_player_exists ${username}`);
        
        if (response.includes('PLAYER_EXISTS:TRUE:ONLINE')) {
            return { exists: true, online: true };
        } else if (response.includes('PLAYER_EXISTS:TRUE:OFFLINE')) {
            return { exists: true, online: false };
        } else {
            return { exists: false, online: false };
        }
    } catch (error) {
        logger.error(`Error checking if player exists: ${error.message}`);
        throw error;
    }
}

/**
 * Give a rank to a player
 * @param {string} username - The player's username
 * @param {string} gamemode - The gamemode
 * @param {string} rank - The rank to give
 * @returns {Promise<object>} - Object with success and message properties
 */
async function giveRank(username, gamemode, rank) {
    try {
        const response = await executeCommand(`giverank ${username} ${gamemode} ${rank}`);
        
        if (response.includes('RANK_GIVEN') && response.includes(':TRUE:')) {
            // Extract transaction ID if present
            const parts = response.split(':');
            const transactionId = parts.length >= 6 ? parts[5] : null;
            
            return { 
                success: true, 
                message: `Successfully gave ${rank} rank to ${username} in ${gamemode} gamemode.`,
                transactionId: transactionId
            };
        } else {
            throw new Error('Failed to give rank. Server response: ' + response);
        }
    } catch (error) {
        logger.error(`Error giving rank: ${error.message}`);
        throw error;
    }
}

/**
 * Give coins to a player
 * @param {string} username - The player's username
 * @param {string} gamemode - The gamemode
 * @param {number} amount - The amount of coins to give
 * @returns {Promise<object>} - Object with success and message properties
 */
async function giveCoins(username, gamemode, amount) {
    try {
        const response = await executeCommand(`givecoin ${username} ${gamemode} ${amount}`);
        
        if (response.includes('COINS_GIVEN') && response.includes(':TRUE:')) {
            // Extract transaction ID if present
            const parts = response.split(':');
            const transactionId = parts.length >= 6 ? parts[5] : null;
            
            return { 
                success: true, 
                message: `Successfully gave ${amount} coins to ${username} in ${gamemode} gamemode.`,
                transactionId: transactionId
            };
        } else {
            throw new Error('Failed to give coins. Server response: ' + response);
        }
    } catch (error) {
        logger.error(`Error giving coins: ${error.message}`);
        throw error;
    }
}

/**
 * Get server status
 * @returns {Promise<object>} - Object with server status information
 */
async function getServerStatus() {
    try {
        // Use list command to get player count
        const listResponse = await executeCommand('list');
        
        // Extract player count from response (format: "There are X of a max of Y players online: [names]")
        const match = listResponse.match(/There are (\d+) of a max of (\d+) players online/);
        const onlinePlayers = match ? parseInt(match[1]) : 0;
        const maxPlayers = match ? parseInt(match[2]) : 0;
        
        // Get TPS (ticks per second) if available
        let tps = null;
        try {
            const tpsResponse = await executeCommand('tps');
            const tpsMatch = tpsResponse.match(/TPS from last 1m, 5m, 15m: ([\d\.]+), ([\d\.]+), ([\d\.]+)/);
            if (tpsMatch) {
                tps = {
                    '1m': parseFloat(tpsMatch[1]),
                    '5m': parseFloat(tpsMatch[2]),
                    '15m': parseFloat(tpsMatch[3])
                };
            }
        } catch (error) {
            // TPS command might not be available, ignore
        }
        
        return {
            online: true,
            players: {
                online: onlinePlayers,
                max: maxPlayers
            },
            tps: tps
        };
    } catch (error) {
        logger.error(`Error getting server status: ${error.message}`);
        return {
            online: false,
            error: error.message
        };
    }
}

/**
 * Broadcast a message to all players on the server
 * @param {string} message - The message to broadcast
 * @returns {Promise<boolean>} - Whether the broadcast was successful
 */
async function broadcastMessage(message) {
    try {
        await executeCommand(`say ${message}`);
        return true;
    } catch (error) {
        logger.error(`Error broadcasting message: ${error.message}`);
        return false;
    }
}

/**
 * Close the RCON connection if open
 */
function closeConnection() {
    if (connectionPool && connectionPool.connected) {
        try {
            connectionPool.disconnect();
            connectionPool = null;
            
            // Clear ping interval if exists
            if (global.rconPingInterval) {
                clearInterval(global.rconPingInterval);
                global.rconPingInterval = null;
            }
            
            logger.info('RCON connection closed');
        } catch (error) {
            logger.error(`Error closing RCON connection: ${error.message}`);
        }
    }
}

module.exports = {
    executeCommand,
    connectRCON,
    executeMinecraftCommand,
    isPlayerExists,
    isPlayerOnline,
    giveRank,
    giveCoins,
    getServerStatus,
    broadcastMessage,
    applyRank,
    closeConnection
}; 