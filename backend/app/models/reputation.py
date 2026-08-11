from sqlalchemy import Column, Integer, String, Float, ForeignKey, BigInteger, DateTime, CHAR
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.sql import func
from app.core.database import Base

class ContributorReputation(Base):
    __tablename__ = "contributor_reputation"

    contributor_id = Column(UUID(as_uuid=True), ForeignKey("contributor.id"), primary_key=True)
    item_category = Column(String, primary_key=True) # produce|packaged|...

    alpha = Column(Float, default=0.0) # Number of agreements
    beta = Column(Float, default=0.0)  # Number of disagreements
    score_lcb = Column(Float, default=0.1) # Lower Confidence Bound (used for trust)

    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
