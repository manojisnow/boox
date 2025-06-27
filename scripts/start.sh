#!/bin/bash

# Start the Spring Boot backend in the background
java -jar backend/app/target/chatapp-1.0-SNAPSHOT.jar &

# Wait for backend to start
sleep 5

# Start the frontend
cd frontend
PORT=3000 npm start 