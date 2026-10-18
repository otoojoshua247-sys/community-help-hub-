> Community-help-hub-
</pre>
Run: java CommunityHelpHub
</pre>

<pre>
Java: A desktop application interface and user interaction.
Python: A backend API for processing requests and managing data.
SQLite: A local database that requires no separate database server.
HTTP communication: Java will communicate with Python through a REST API.
Free access: No subscriptions or paid APIs are required to run the project locally.
</pre>

# You need to know 

This project uses only Python's standard library, so no external Python packages are required.


> Community Help Hub
> Python 3.10 or newer
> No external dependencies required

<pre>
Method
          Endpoint 
                         Purpose
 GET    /api/health    Check server health
 GET    /api/requests  List requests
 GET    /api/requests/1 Viewaparticular request
POST   /api/requests  Create a request
PATCH  /api/requests/1 Update a request's status

</pre>