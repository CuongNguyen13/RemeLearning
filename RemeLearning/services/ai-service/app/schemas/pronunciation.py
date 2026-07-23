from pydantic import BaseModel


class PhonemeScoreResponse(BaseModel):
    ipa: str
    score: float


class WordScoreResponse(BaseModel):
    word: str
    score: float
    phonemes: list[PhonemeScoreResponse]


class PronunciationScoreResponse(BaseModel):
    overall: float
    words: list[WordScoreResponse]
    transcript: str
    weak_phonemes: list[str]
