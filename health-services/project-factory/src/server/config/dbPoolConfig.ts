import { Pool } from 'pg';
import config from '.';

const pool = new Pool({
    user: config.DB_CONFIG.DB_USER,
    host: config.DB_CONFIG.DB_HOST,
    database: config.DB_CONFIG.DB_NAME,
    password: config.DB_CONFIG.DB_PASSWORD,
    port: parseInt(config.DB_CONFIG.DB_PORT),
    ssl: {
        rejectUnauthorized: false,
      },
});

export default pool;