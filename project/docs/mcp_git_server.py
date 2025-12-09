#!/usr/bin/env python3
"""
Простой MCP сервер для получения информации о git ветке
Используется для демонстрации интеграции MCP с проектом
"""

import json
import sys
import subprocess
from typing import Dict, Any


class GitMCPServer:
    """MCP сервер для git операций"""

    def __init__(self):
        self.tools = {
            "git_current_branch": {
                "name": "git_current_branch",
                "description": "Получает имя текущей git ветки",
                "inputSchema": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            },
            "git_branch_info": {
                "name": "git_branch_info",
                "description": "Получает детальную информацию о текущей ветке",
                "inputSchema": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        }

    def get_current_branch(self) -> str:
        """Возвращает имя текущей ветки"""
        try:
            result = subprocess.run(
                ['git', 'branch', '--show-current'],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            return f"Error: {e.stderr}"

    def get_branch_info(self) -> Dict[str, Any]:
        """Возвращает детальную информацию о ветке"""
        try:
            branch = self.get_current_branch()

            # Статус
            status_result = subprocess.run(
                ['git', 'status', '--short', '--branch'],
                capture_output=True,
                text=True,
                check=True
            )

            # Последний коммит
            commit_result = subprocess.run(
                ['git', 'log', '-1', '--oneline'],
                capture_output=True,
                text=True,
                check=True
            )

            # Список веток
            branches_result = subprocess.run(
                ['git', 'branch', '--list'],
                capture_output=True,
                text=True,
                check=True
            )

            return {
                "current_branch": branch,
                "status": status_result.stdout.strip(),
                "last_commit": commit_result.stdout.strip(),
                "all_branches": [
                    b.strip().replace('* ', '')
                    for b in branches_result.stdout.strip().split('\n')
                ]
            }
        except subprocess.CalledProcessError as e:
            return {"error": str(e)}

    def handle_request(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """Обрабатывает MCP запрос"""
        method = request.get("method")
        params = request.get("params", {})

        if method == "initialize":
            return {
                "protocolVersion": "0.1.0",
                "serverInfo": {
                    "name": "git-mcp-server",
                    "version": "1.0.0"
                },
                "capabilities": {
                    "tools": {}
                }
            }

        elif method == "tools/list":
            return {
                "tools": list(self.tools.values())
            }

        elif method == "tools/call":
            tool_name = params.get("name")

            if tool_name == "git_current_branch":
                branch = self.get_current_branch()
                return {
                    "content": [
                        {
                            "type": "text",
                            "text": f"Текущая ветка: {branch}"
                        }
                    ]
                }

            elif tool_name == "git_branch_info":
                info = self.get_branch_info()
                return {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(info, ensure_ascii=False, indent=2)
                        }
                    ]
                }

            return {"error": "Unknown tool"}

        return {"error": "Unknown method"}

    def run(self):
        """Запускает MCP сервер"""
        print("MCP Git Server started", file=sys.stderr)

        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue

            try:
                request = json.loads(line)
                response = self.handle_request(request)

                output = json.dumps({
                    "jsonrpc": "2.0",
                    "id": request.get("id"),
                    "result": response
                })

                print(output)
                sys.stdout.flush()

            except Exception as e:
                error_response = json.dumps({
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {
                        "code": -32700,
                        "message": str(e)
                    }
                })
                print(error_response)
                sys.stdout.flush()


def main():
    """Главная функция"""
    if len(sys.argv) > 1 and sys.argv[1] == "test":
        # Тестовый режим
        server = GitMCPServer()

        print("=== Тест MCP Git Server ===\n")

        print("1. Текущая ветка:")
        print(f"   {server.get_current_branch()}\n")

        print("2. Информация о ветке:")
        info = server.get_branch_info()
        print(json.dumps(info, ensure_ascii=False, indent=2))

    else:
        # Запуск MCP сервера
        server = GitMCPServer()
        server.run()


if __name__ == "__main__":
    main()
