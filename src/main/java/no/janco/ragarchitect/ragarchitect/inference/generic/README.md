# Generic Inference Service

This README provides guidelines on how to create a service that can be used with the generic inference handler in the RAG Architect project.

## Overview

The generic inference handler is designed to work with a variety of LLM (Large Language Model) services. To create a compatible service, you need to implement an API that adheres to the expected request and response format.

## API Specification

### Endpoint

- POST `/inference`

### Request Format

The service should expect a POST request with a JSON body in the following format:

```json
{
  "message": "Your prompt or query here"
}
```

### Response Format

The service should return a JSON response in the following format:

```json
{
  "response": "The generated response from the LLM"
}
```

## Implementation Guidelines

1. Create a new service that exposes an HTTP endpoint for inference.
2. The service should accept POST requests at the `/inference` endpoint.
3. Parse the incoming JSON request to extract the `message` field.
4. Process the message using your LLM or other inference logic.
5. Format the response as a JSON object with a `response` field containing the generated text.
6. Return the response with a 200 OK status code.

## Example Implementation (Python with Flask)

Here's a simple example of how you might implement this service using Python and Flask:

```python
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/inference', methods=['POST'])
def inference():
    data = request.json
    message = data.get('message', '')
    
    # Your LLM inference logic here
    response = your_llm_function(message)
    
    return jsonify({"response": response})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
```

## Configuration

To use your custom service with the RAG Architect's generic inference handler:

1. Deploy your service and note its URL.
2. In the RAG Architect configuration, set the `GENERIC_SERVER_URL` environment variable to your service's URL.
3. Ensure the `SPRING_PROFILES_ACTIVE` environment variable is set to `generic`.

## Security Considerations

- Implement appropriate authentication and authorization mechanisms.
- Use HTTPS for all communications.
- Validate and sanitize all input to prevent injection attacks.

## Testing

Before integrating with RAG Architect, test your service independently:

```bash
curl -X POST http://your-service-url/inference \
     -H "Content-Type: application/json" \
     -d '{"message": "Test prompt"}'
```

Ensure you receive a properly formatted JSON response.

## Troubleshooting

- Check logs for any errors or unexpected behavior.
- Verify that your service is accessible from the RAG Architect container network.
- Ensure that your service can handle the expected request volume and payload sizes.

By following these guidelines, you can create a custom inference service that seamlessly integrates with the RAG Architect's generic inference handler.