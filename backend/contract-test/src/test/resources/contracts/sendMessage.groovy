org.springframework.cloud.contract.spec.Contract.make {
    description "Send a chat message and receive a response"
    request {
        method 'POST'
        url '/api/chat/send'
        headers {
            contentType(applicationJson())
        }
        body(
            sessionId: $(consumer(regex("[a-zA-Z0-9\\-]+")), producer('test-session')),
            message: $(consumer('Hello!'), producer('Hello!')),
            server: $(consumer('ollama'), producer('ollama')),
            model: $(consumer('default'), producer('default')),
            stream: $(consumer(false), producer(false))
        )
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body(
            role: 'assistant',
            content: $(consumer(anyNonBlankString()), producer('Hi!'))
        )
    }
} 