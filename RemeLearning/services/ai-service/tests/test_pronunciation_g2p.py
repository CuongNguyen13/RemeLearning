from app.pronunciation.arpabet_ipa import arpabet_to_ipa
from app.pronunciation.g2p import G2pEnProvider


def test_g2p_en_splits_into_words_with_stress_stripped():
    provider = G2pEnProvider()

    words = provider.phonemize("This is a test.")

    assert [w.word for w in words] == ["This", "is", "a", "test"]
    assert words[0].phones == ["DH", "IH", "S"]
    # No stress digits leak through (G2p emits "IH1" etc.).
    assert all(not phone[-1].isdigit() for word in words for phone in word.phones)


def test_arpabet_to_ipa_covers_the_full_cmudict_inventory():
    cmudict_phones = [
        "AA", "AE", "AH", "AO", "AW", "AY", "B", "CH", "D", "DH", "EH", "ER", "EY", "F", "G",
        "HH", "IH", "IY", "JH", "K", "L", "M", "N", "NG", "OW", "OY", "P", "R", "S", "SH", "T",
        "TH", "UH", "UW", "V", "W", "Y", "Z", "ZH",
    ]

    for phone in cmudict_phones:
        assert arpabet_to_ipa(phone) is not None, f"missing IPA mapping for {phone}"


def test_arpabet_to_ipa_returns_none_for_unknown_symbol():
    assert arpabet_to_ipa("NOT_A_PHONE") is None
