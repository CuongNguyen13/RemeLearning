"""Maps CMUdict/ARPAbet phone symbols (what `g2p_en` emits, stress digits already stripped by
`app.pronunciation.g2p`) to the IPA symbols `facebook/wav2vec2-lv-60-espeak-cv-ft` was fine-tuned
on (its output vocabulary is espeak's IPA phoneme set, not ARPAbet - see that model's card).
Values below were checked against the model's actual `vocab.json` (392 entries); every General
American English phone the CMUdict/ARPAbet inventory (39 phones) uses is present.
"""

from __future__ import annotations

# General American English, CMUdict's 39-phone ARPAbet inventory -> espeak/IPA.
ARPABET_TO_IPA: dict[str, str] = {
    "AA": "ɑ",
    "AE": "æ",
    "AH": "ʌ",
    "AO": "ɔ",
    "AW": "aʊ",
    "AY": "aɪ",
    "B": "b",
    "CH": "tʃ",
    "D": "d",
    "DH": "ð",
    "EH": "ɛ",
    "ER": "ɚ",
    "EY": "eɪ",
    "F": "f",
    "G": "ɡ",
    "HH": "h",
    "IH": "ɪ",
    "IY": "i",
    "JH": "dʒ",
    "K": "k",
    "L": "l",
    "M": "m",
    "N": "n",
    "NG": "ŋ",
    "OW": "oʊ",
    "OY": "ɔɪ",
    "P": "p",
    "R": "ɹ",
    "S": "s",
    "SH": "ʃ",
    "T": "t",
    "TH": "θ",
    "UH": "ʊ",
    "UW": "u",
    "V": "v",
    "W": "w",
    "Y": "j",
    "Z": "z",
    "ZH": "ʒ",
}


def arpabet_to_ipa(phone: str) -> str | None:
    """None for an unrecognized ARPAbet symbol - callers must skip rather than guess."""
    return ARPABET_TO_IPA.get(phone)
