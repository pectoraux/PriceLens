from pydantic import BaseModel
from datetime import datetime

class NonceResponse(BaseModel):
    nonce: str
    expiresAt: datetime
