# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.1-beta] - 2026-01-29

### Added
- **CactusModelManager** - Standalone model management singleton
  - `getDownloadedModels()` - List all locally downloaded model slugs
  - `isModelDownloaded(modelSlug)` - Check if a specific model exists
  - `deleteModel(modelSlug)` - Delete models to free storage
  - `getModelsDirectory()` - Get models storage directory path
- ModelManagerPage to example app demonstrating new functionality
- Comprehensive model management documentation in README

### Changed
- Updated README with Model Management section
- Enhanced example app with model management demo

### Fixed
- N/A

## [1.4.0-beta] - 2025-12-27

### Added
- Initial Kotlin Multiplatform release
- CactusLM for language model inference
- CactusSTT for speech-to-text
- Support for iOS and Android platforms
- Model discovery and download capabilities
- Streaming completions
- Function calling support
- Vision model support
- Embedding generation

[1.4.1-beta]: https://github.com/cactus-compute/cactus-kotlin/compare/v1.4.0-beta...v1.4.1-beta
[1.4.0-beta]: https://github.com/cactus-compute/cactus-kotlin/releases/tag/v1.4.0-beta
