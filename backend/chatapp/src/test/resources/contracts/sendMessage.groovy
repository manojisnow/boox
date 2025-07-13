package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    request {
        method 'POST'
        url '/api/chat/send'
        body(
                message: "hello",
                server: "ollama",
                model: "llama2",
                sessionId: "12345",
                stream: false
        )
        headers {
            contentType('application/json')
        }
    }
    response {
        status 200
        body(
                role: "assistant",
                content: "Greetings!"
        )
        headers {
            contentType('application/json')
        }
    }
}
