# Central Workflow Pipeline

## Pipeline Flow Overview

This pipeline is designed to improve and ensure quality of a project throughout the entire course of its development. This starts with Project Framing where the project is initialized by with an overview of the idea of the project, defining the scope, the functional and non-functional requirements necessary to complete the project, a risk analysis, a feasibility analysis to ensure the project is worth pursuing, and a list of slices to complete the project. The pipeline allows the project manager to ensure all the necessary documentation is present with correct formatting and spelling before moving onto the next stage of development.

## Project Framing

In this stage, the pipeline is all about overseeing the documentation that goes into planning the initial overview of the project. The project manager will fill out a config file (`framing.yml` by default) that will list each document section that is needed for the project as well as any subsections. This pipeline is built to break each section into its own markdown document to allow multiple team members to more easily work on different sections of the documentation at the same time without sending repeated git commits to edit one shared document. The subsections can be used to ensure the correct points are addressed in each section document when automated file verification occurs. 

This pipeline works by detecting changes made to the documents within the dedicated Framing document directory (`docs/framing` by default). This ensures better pipeline performance by not having these same checks occur when the project moves onto different stages, including other documentation stages like Slice Framing. For each Framing document that a change has occurred to, the pipeline will automatically run *markdownlint* to ensure correct markdown formatting is being used and *cspell* to ensure proper spelling within the documents.

Each document should have a status header to indicate whether it is still being drafted or is completed. As an example:
```
---
status: draft
---
```
This document supports `draft` as a drafted status indicator and `complete` as a completed status indicator. All documents must be marked as complete before moving onto the next stage. Once all documents have the `complete` status indicator, the pipeline will request a signoff from the project manager to ensure the documentation is not being marked as complete prematurely. Once the signoff happens, the Project Framing documentation is compiled with *pandoc* to put all the documentation sections into one document and outputted within the Framing document directory. 