from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, BigInteger, DateTime, LargeBinary
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.core.database import Base

class TaxonomyItem(Base):
    __tablename__ = "taxonomy_item"

    id = Column(BigInteger, primary_key=True, index=True)
    slug = Column(String, unique=True, nullable=False)
    parent_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), nullable=True)
    canonical_name = Column(String, nullable=False)
    scientific_name = Column(String)
    category = Column(String, nullable=False)
    default_unit = Column(String, nullable=False)
    density_kg_per_l = Column(Integer)  # Stored as minor or fixed point if needed
    shape_model = Column(String)
    is_perishable = Column(Boolean, default=True)
    status = Column(String, default="active")
    merged_into_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), nullable=True)
    version = Column(Integer, default=1)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    names = relationship("TaxonomyItemName", back_populates="item")
    embedding = relationship("TaxonomyItemEmbedding", back_populates="item", uselist=False)

class TaxonomyItemName(Base):
    __tablename__ = "taxonomy_item_name"

    item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), primary_key=True)
    locale = Column(String, primary_key=True)
    name = Column(String, primary_key=True)
    is_primary = Column(Boolean, default=False)
    is_vernacular = Column(Boolean, default=False)

    item = relationship("TaxonomyItem", back_populates="names")

class TaxonomyItemEmbedding(Base):
    __tablename__ = "taxonomy_item_embedding"

    item_id = Column(BigInteger, ForeignKey("taxonomy_item.id"), primary_key=True)
    text_embedding = Column(LargeBinary, nullable=False)
    model_version = Column(String, nullable=False)

    item = relationship("TaxonomyItem", back_populates="embedding")
