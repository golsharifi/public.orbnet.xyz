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
          return new Locale("af");
        case "AR":
          return new Locale("ar");
        case "BG":
          return new Locale("bg");
        case "CS":
          return new Locale("cs");
        case "DA":
          return new Locale("da");
        case "FA":
          return new Locale("fa");
        case "EL":
          return new Locale("el");
        case "ES":
          return new Locale("es");
        case "ET":
          return new Locale("et");
        case "FI":
          return new Locale("fi");
        case "HE":
          return new Locale("he");
        case "HI":
          return new Locale("hi");
        case "HR":
          return new Locale("hr");
        case "HU":
          return new Locale("hu");
        case "ID":
          return new Locale("id");
        case "IS":
          return new Locale("is");
        case "LT":
          return new Locale("lt");
        case "LV":
          return new Locale("lv");
        case "NL":
          return new Locale("nl");
        case "NO":
          return new Locale("no");
        case "PL":
          return new Locale("pl");
        case "PT":
          return new Locale("pt");
        case "RO":
          return new Locale("ro");
        case "RU":
          return new Locale("ru");
        case "SK":
          return new Locale("sk");
        case "SL":
          return new Locale("sl");
        case "SV":
          return new Locale("sv");
        case "TR":
          return new Locale("tr");
        case "VI":
          return new Locale("vi");
        default:
          return new Locale(languageCode.toLowerCase());
      }
    }
    return null;
  }
}