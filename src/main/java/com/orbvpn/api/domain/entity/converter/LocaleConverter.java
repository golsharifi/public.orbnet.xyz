package com.orbvpn.api.domain.entity.converter;

import java.util.Locale;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LocaleConverter implements AttributeConverter<Locale, String> {

  @Override
  public String convertToDatabaseColumn(Locale locale) {
    if (locale != null) {
      return locale.getLanguage().toUpperCase();
    }
    return null;
  }

  @Override
  public Locale convertToEntityAttribute(String languageCode) {
    if (languageCode != null && !languageCode.isEmpty()) {
      switch (languageCode.toUpperCase()) {
        case "EN":
          return Locale.ENGLISH;
        case "FR":
          return Locale.FRENCH;
        case "DE":
          return Locale.GERMAN;
        case "IT":
          return Locale.ITALIAN;
        case "JA":
          return Locale.JAPANESE;
        case "KO":
          return Locale.KOREAN;
        case "ZH-CN":
          return Locale.SIMPLIFIED_CHINESE;
        case "ZH-TW":
          return Locale.TRADITIONAL_CHINESE;
        case "ZH":
          return Locale.CHINESE;
        case "US":
          return Locale.US;
        case "UK":
          return Locale.UK;
        case "CA":
          return Locale.CANADA;
        case "CA-FR":
          return Locale.CANADA_FRENCH;
        case "CHINA":
        case "CN":
          return Locale.CHINA;
        case "PRC":
          return Locale.PRC;
        case "FRANCE":
          return Locale.FRANCE;
        case "GERMANY":
          return Locale.GERMANY;
        case "ITALY":
          return Locale.ITALY;
        case "JAPAN":
          return Locale.JAPAN;
        case "KOREA":
          return Locale.KOREA;
        case "TAIWAN":
          return Locale.TAIWAN;
        case "AF":
          return Locale.of("af");
        case "AR":
          return Locale.of("ar");
        case "BG":
          return Locale.of("bg");
        case "CS":
          return Locale.of("cs");
        case "DA":
          return Locale.of("da");
        case "FA":
          return Locale.of("fa");
        case "EL":
          return Locale.of("el");
        case "ES":
          return Locale.of("es");
        case "ET":
          return Locale.of("et");
        case "FI":
          return Locale.of("fi");
        case "HE":
          return Locale.of("he");
        case "HI":
          return Locale.of("hi");
        case "HR":
          return Locale.of("hr");
        case "HU":
          return Locale.of("hu");
        case "ID":
          return Locale.of("id");
        case "IS":
          return Locale.of("is");
        case "LT":
          return Locale.of("lt");
        case "LV":
          return Locale.of("lv");
        case "NL":
          return Locale.of("nl");
        case "NO":
          return Locale.of("no");
        case "PL":
          return Locale.of("pl");
        case "PT":
          return Locale.of("pt");
        case "RO":
          return Locale.of("ro");
        case "RU":
          return Locale.of("ru");
        case "SK":
          return Locale.of("sk");
        case "SL":
          return Locale.of("sl");
        case "SV":
          return Locale.of("sv");
        case "TR":
          return Locale.of("tr");
        case "VI":
          return Locale.of("vi");
        default:
          return Locale.of(languageCode.toLowerCase());
      }
    }
    return null;
  }
}