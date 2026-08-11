from pydantic import BaseModel
from typing import List, Optional

class ModelEntry(BaseModel):
    name: str
    version: str
    url: str
    sha256: str
    signature: str
    minRamMb: int
    recommendedDelegate: str

class ModelManifest(BaseModel):
    models: List[ModelEntry]
