#!/bin/bash
# Script to create and upload cryptography Lambda layer for x86_64 architecture

set -e

LAYER_NAME="cryptography-layer"
PYTHON_VERSION="python3.13"

REGION="${1:?Usage: $0 <aws-region> (e.g., us-east-1)}"

echo "Creating Lambda layer for cryptography (x86_64 architecture)..."
echo "Using region: ${REGION}"

# Clean up any existing layer directory
rm -rf layer

# Create temporary directory
mkdir -p layer/python

# Install cryptography for Linux x86_64 platform (Lambda runtime)
pip install \
    --platform manylinux2014_x86_64 \
    --target layer/python \
    --implementation cp \
    --python-version 3.13 \
    --only-binary=:all: \
    --upgrade \
    cryptography

# Create zip file
cd layer
zip -r ../cryptography-layer.zip .
cd ..

# Upload to Lambda
LAYER_ARN=$(aws lambda publish-layer-version \
    --layer-name ${LAYER_NAME} \
    --description "Cryptography library for Python 3.13 (x86_64)" \
    --zip-file fileb://cryptography-layer.zip \
    --compatible-runtimes ${PYTHON_VERSION} \
    --region "${REGION}" \
    --query 'LayerVersionArn' \
    --output text)

echo "Layer created successfully!"
echo "Layer ARN: ${LAYER_ARN}"
export LAYER_ARN="${LAYER_ARN}"

# Cleanup
rm -rf layer cryptography-layer.zip
