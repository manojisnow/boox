# React Spring Boot Chat Application

This project is a chat application that integrates a React-based frontend with a Spring Boot backend. The application utilizes the Ollama-hosted Llama 3.2 model for AI-driven conversations and includes functionality for web access through DuckDuckGo.

## Project Structure

- **backend/**: Contains the Spring Boot application.
  - **src/**: Source code for the backend.
    - **main/**: Main application code.
      - **java/com/example/chatapp/**: Java classes for the application.
        - `Application.java`: Main entry point for the Spring Boot application.
        - `ChatController.java`: Handles HTTP requests related to chat functionality.
        - `ChatService.java`: Contains business logic for interacting with the Llama model and DuckDuckGo.
      - **resources/**: Configuration and template files.
        - `application.properties`: Configuration properties for the Spring Boot application.
        - **templates/**: Directory for server-side templates (if needed).
    - **test/**: Unit tests for the application.
      - **java/com/example/chatapp/**: Test classes for the application.
        - `ChatServiceTest.java`: Unit tests for the ChatService class.
  - `pom.xml`: Maven configuration file for the backend.

- **frontend/**: Contains the React application.
  - **src/**: Source code for the frontend.
    - **components/**: React components for the application.
      - `ChatBox.jsx`: UI for users to input messages and view the conversation.
      - `Message.jsx`: Displays individual messages in the chat interface.
    - **services/**: API service for making calls to the backend.
      - `api.js`: Functions for sending messages and retrieving responses.
    - `App.jsx`: Main application component that sets up the chat interface.
    - `index.js`: Entry point for the React application.
  - **public/**: Public assets for the frontend.
    - `index.html`: Main HTML file for the React application.
  - `package.json`: Configuration file for npm, listing dependencies and scripts.
  - `README.md`: Documentation specific to the frontend part of the project.

## Setup Instructions

1. **Backend Setup**:
   - Navigate to the `backend` directory.
   - Run `mvn clean install` to build the backend application.
   - Configure the `application.properties` file with necessary API keys and URLs.
   - Start the Spring Boot application using `mvn spring-boot:run`.

2. **Frontend Setup**:
   - Navigate to the `frontend` directory.
   - Run `npm install` to install the required dependencies.
   - Start the React application using `npm start`.

## Usage

- Open your browser and navigate to `http://localhost:3000` to access the chat application.
- Users can input messages, and the application will communicate with the backend to generate responses using the Llama model.
- The application also supports web searches through DuckDuckGo.

## Contributing

Feel free to submit issues or pull requests for improvements or bug fixes.