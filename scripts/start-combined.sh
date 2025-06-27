#!/bin/sh

# Start the backend in the background
java -jar /app/app.jar &

# Wait for backend to be ready
while ! curl -s http://localhost:8080/actuator/health > /dev/null; do
    echo "Waiting for backend to start..."
    sleep 2
done

# Start the frontend based on environment
if [ "$NODE_ENV" = "production" ]; then
    cd /app/frontend && serve -s build -l 3000
else
    cd /app/frontend && npm start
fi 