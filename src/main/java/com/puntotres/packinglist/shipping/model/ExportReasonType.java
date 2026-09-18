package com.puntotres.packinglist.shipping.model;

/** Los exportReasonType del OpenAPI de MyDHL (3.3.2). codigoDhl() es el literal que viaja en el JSON. */
public enum ExportReasonType {
    PERMANENT("permanent"), TEMPORARY("temporary"), RETURN("return"),
    USED_EXHIBITION_GOODS_TO_ORIGIN("used_exhibition_goods_to_origin"),
    INTERCOMPANY_USE("intercompany_use"), COMMERCIAL_PURPOSE_OR_SALE("commercial_purpose_or_sale"),
    PERSONAL_BELONGINGS_OR_PERSONAL_USE("personal_belongings_or_personal_use"),
    SAMPLE("sample"), GIFT("gift"), RETURN_TO_ORIGIN("return_to_origin"),
    WARRANTY_REPLACEMENT("warranty_replacement");

    private final String codigoDhl;
    ExportReasonType(String codigoDhl) { this.codigoDhl = codigoDhl; }
    public String codigoDhl() { return codigoDhl; }
}
