#!/usr/bin/env node
/**
 * MCP сервер для получения информации о текущей git ветке
 * Простая реализация для демонстрации MCP
 */

const { exec } = require('child_process');
const { promisify } = require('util');
const execAsync = promisify(exec);

// Простой MCP протокол через stdio
class MCPGitServer {
  constructor() {
    this.tools = {
      'git_current_branch': {
        name: 'git_current_branch',
        description: 'Получает имя текущей git ветки',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      },
      'git_branch_info': {
        name: 'git_branch_info',
        description: 'Получает информацию о текущей ветке (имя, статус, коммиты)',
        inputSchema: {
          type: 'object',
          properties: {},
          required: []
        }
      }
    };
  }

  async getCurrentBranch() {
    try {
      const { stdout } = await execAsync('git branch --show-current');
      return stdout.trim();
    } catch (error) {
      return `Error: ${error.message}`;
    }
  }

  async getBranchInfo() {
    try {
      const branch = await this.getCurrentBranch();
      const { stdout: status } = await execAsync('git status --short --branch');
      const { stdout: lastCommit } = await execAsync('git log -1 --oneline');

      return {
        branch,
        status: status.trim(),
        lastCommit: lastCommit.trim()
      };
    } catch (error) {
      return { error: error.message };
    }
  }

  async handleRequest(request) {
    const { method, params } = request;

    switch (method) {
      case 'initialize':
        return {
          protocolVersion: '0.1.0',
          serverInfo: {
            name: 'git-mcp-server',
            version: '1.0.0'
          },
          capabilities: {
            tools: {}
          }
        };

      case 'tools/list':
        return {
          tools: Object.values(this.tools)
        };

      case 'tools/call':
        const { name } = params;

        if (name === 'git_current_branch') {
          const branch = await this.getCurrentBranch();
          return {
            content: [
              {
                type: 'text',
                text: `Текущая ветка: ${branch}`
              }
            ]
          };
        }

        if (name === 'git_branch_info') {
          const info = await this.getBranchInfo();
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify(info, null, 2)
              }
            ]
          };
        }

        return { error: 'Unknown tool' };

      default:
        return { error: 'Unknown method' };
    }
  }

  async start() {
    console.error('MCP Git Server started');

    // Читаем JSON-RPC запросы из stdin
    process.stdin.setEncoding('utf8');

    let buffer = '';

    process.stdin.on('data', async (chunk) => {
      buffer += chunk;

      // Обрабатываем каждую строку как отдельный запрос
      const lines = buffer.split('\n');
      buffer = lines.pop(); // Сохраняем неполную строку

      for (const line of lines) {
        if (!line.trim()) continue;

        try {
          const request = JSON.parse(line);
          const response = await this.handleRequest(request);

          // Отправляем ответ в stdout
          process.stdout.write(JSON.stringify({
            jsonrpc: '2.0',
            id: request.id,
            result: response
          }) + '\n');
        } catch (error) {
          console.error('Error processing request:', error);
          process.stdout.write(JSON.stringify({
            jsonrpc: '2.0',
            id: null,
            error: {
              code: -32700,
              message: error.message
            }
          }) + '\n');
        }
      }
    });

    process.stdin.on('end', () => {
      console.error('MCP Git Server stopped');
      process.exit(0);
    });
  }
}

// Запускаем сервер
const server = new MCPGitServer();
server.start();
