import json
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs

HOST = "127.0.0.1"
PORT = 8000

BASE_DIR = Path(__file__).resolve().parent
DATABASE = BASE_DIR / "community.db"

CATEGORIES = {
    "Food",
    "Education",
    "Healthcare",
    "Shelter",
    "Employment",
    "Other",
}

STATUSES = {
    "Open",
    "In Progress",
    "Resolved",
}


def connect_db():
    connection = sqlite3.connect(DATABASE, timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database():
    with connect_db() as connection:
        connection.execute("""
            CREATE TABLE IF NOT EXISTS requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                location TEXT NOT NULL,
                contact TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'Open',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)


def request_to_dict(row):
    return dict(row)


class APIHandler(BaseHTTPRequestHandler):

    def send_json(self, status_code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")

        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        content_length = int(self.headers.get("Content-Length", "0"))

        if content_length <= 0 or content_length > 16384:
            raise ValueError("Request body must be between 1 and 16384 bytes.")

        raw_body = self.rfile.read(content_length)
        data = json.loads(raw_body.decode("utf-8"))

        if not isinstance(data, dict):
            raise ValueError("The request body must be a JSON object.")

        return data

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/api/health":
            self.send_json(200, {
                "success": True,
                "message": "Community Help Hub API is running."
            })
            return

        if parsed.path == "/api/requests":
            self.list_requests(parsed.query)
            return

        if parsed.path.startswith("/api/requests/"):
            self.get_request(parsed.path)
            return

        self.send_json(404, {
            "success": False,
            "error": "Endpoint not found."
        })

    def list_requests(self, query_string):
        parameters = parse_qs(query_string)
        category = parameters.get("category", [""])[0].strip()
        search = parameters.get("search", [""])[0].strip()

        if len(category) > 50 or len(search) > 100:
            self.send_json(400, {
                "success": False,
                "error": "Search filters are too long."
            })
            return

        sql = "SELECT * FROM requests WHERE 1=1"
        values = []

        if category:
            if category not in CATEGORIES:
                self.send_json(400, {
                    "success": False,
                    "error": "Invalid category."
                })
                return

            sql += " AND category = ?"
            values.append(category)

        if search:
            sql += """
                AND (
                    title LIKE ? OR
                    description LIKE ? OR
                    location LIKE ?
                )
            """
            term = f"%{search}%"
            values.extend([term, term, term])

        sql += " ORDER BY id DESC LIMIT 200"

        with connect_db() as connection:
            rows = connection.execute(sql, values).fetchall()

        self.send_json(200, {
            "success": True,
            "requests": [request_to_dict(row) for row in rows]
        })

    def get_request(self, path):
        request_id = self.extract_id(path)

        if request_id is None:
            self.send_json(400, {
                "success": False,
                "error": "Invalid request ID."
            })
            return

        with connect_db() as connection:
            row = connection.execute(
                "SELECT * FROM requests WHERE id = ?",
                (request_id,)
            ).fetchone()

        if row is None:
            self.send_json(404, {
                "success": False,
                "error": "Request not found."
            })
            return

        self.send_json(200, {
            "success": True,
            "request": request_to_dict(row)
        })

    def do_POST(self):
        if urlparse(self.path).path != "/api/requests":
            self.send_json(404, {
                "success": False,
                "error": "Endpoint not found."
            })
            return

        try:
            data = self.read_json()
            fields = {}

            for field in (
                "title",
                "description",
                "category",
                "location",
                "contact",
            ):
                value = data.get(field, "")

                if not isinstance(value, str):
                    raise ValueError(f"{field} must be text.")

                fields[field] = value.strip()

            limits = {
                "title": 120,
                "description": 3000,
                "category": 50,
                "location": 150,
                "contact": 200,
            }

            for field, maximum in limits.items():
                if len(fields[field]) > maximum:
                    raise ValueError(
                        f"{field} cannot exceed {maximum} characters."
                    )

            for field in (
                "title",
                "description",
                "category",
                "location",
                "contact",
            ):
                if not fields[field]:
                    raise ValueError(f"{field} is required.")

            if fields["category"] not in CATEGORIES:
                raise ValueError("Please select a valid category.")

            with connect_db() as connection:
                cursor = connection.execute("""
                    INSERT INTO requests (
                        title,
                        description,
                        category,
                        location,
                        contact
                    )
                    VALUES (?, ?, ?, ?, ?)
                """, (
                    fields["title"],
                    fields["description"],
                    fields["category"],
                    fields["location"],
                    fields["contact"],
                ))

                request_id = cursor.lastrowid

                row = connection.execute(
                    "SELECT * FROM requests WHERE id = ?",
                    (request_id,)
                ).fetchone()

            self.send_json(201, {
                "success": True,
                "message": "Assistance request created.",
                "request": request_to_dict(row)
            })

        except (ValueError, json.JSONDecodeError, UnicodeDecodeError) as error:
            self.send_json(400, {
                "success": False,
                "error": str(error)
            })

    def do_PATCH(self):
        parsed = urlparse(self.path)

        if not parsed.path.startswith("/api/requests/"):
            self.send_json(404, {
                "success": False,
                "error": "Endpoint not found."
            })
            return

        request_id = self.extract_id(parsed.path)

        if request_id is None:
            self.send_json(400, {
                "success": False,
                "error": "Invalid request ID."
            })
            return

        try:
            data = self.read_json()
            status = data.get("status")

            if status not in STATUSES:
                self.send_json(400, {
                    "success": False,
                    "error": "Invalid status."
                })
                return

            with connect_db() as connection:
                cursor = connection.execute(
                    "UPDATE requests SET status = ? WHERE id = ?",
                    (status, request_id)
                )

                if cursor.rowcount == 0:
                    self.send_json(404, {
                        "success": False,
                        "error": "Request not found."
                    })
                    return

                row = connection.execute(
                    "SELECT * FROM requests WHERE id = ?",
                    (request_id,)
                ).fetchone()

            self.send_json(200, {
                "success": True,
                "message": "Request status updated.",
                "request": request_to_dict(row)
            })

        except (
            ValueError,
            json.JSONDecodeError,
            UnicodeDecodeError,
        ) as error:
            self.send_json(400, {
                "success": False,
                "error": str(error)
            })

    def extract_id(self, path):
        prefix = "/api/requests/"

        if not path.startswith(prefix):
            return None

        value = path[len(prefix):]

        if not value.isdigit():
            return None

        request_id = int(value)

        if request_id <= 0:
            return None

        return request_id

    def do_DELETE(self):
        self.send_json(405, {
            "success": False,
            "error": "Deleting requests is not supported."
        })

    def log_message(self, format_string, *args):
        print(
            f"[{self.log_date_time_string()}] "
            f"{format_string % args}"
        )


def main():
    initialize_database()

    server = ThreadingHTTPServer((HOST, PORT), APIHandler)

    print("Community Help Hub API is running.")
    print(f"Address: http://{HOST}:{PORT}")
    print("Press Ctrl+C to stop.")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()