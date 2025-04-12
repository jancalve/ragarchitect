This script is to dowload all models you want to use locally
- The models are downloaded once and shared between all docker builds, ie. faster build times
- This bypasses any issues with self-signed certificates

NB: You must authenticate using huggingface-cli login in order to download the models.