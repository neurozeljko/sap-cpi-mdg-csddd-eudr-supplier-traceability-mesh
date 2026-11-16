import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

def Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def payload = new JsonSlurper().parse(reader)

    String supplierName = (payload.SupplierLegalName ?: "").trim()
    String countryCode = (payload.Country ?: "").trim().toUpperCase()
    String leiCode = (payload.LEI ?: "").replaceAll("\\s+", "").toUpperCase()
    String commodityHsCode = (payload.CommodityHsCode ?: "").replaceAll("[^0-9]", "")
    BigDecimal csdddAuditScore = (payload.CsdddAuditScore ?: 0.0) as BigDecimal
    int criticalFindings = (payload.CriticalHumanRightsFindings ?: 0) as int
    String auditValidUntilStr = (payload.AuditValidUntil ?: "").trim()
    List geoPlots = (payload.EudrGeoPlots ?: []) as List

    List<String> complianceViolations = []
    if (!supplierName || supplierName.length() < 3) complianceViolations.add("INVALID_SUPPLIER_NAME: SupplierLegalName must contain at least 3 characters.")
    if (!countryCode.matches("^[A-Z]{2}\$")) complianceViolations.add("INVALID_COUNTRY_CODE: ISO 3166-1 alpha-2 Country code is required.")
    if (!isValidIso17442Lei(leiCode)) complianceViolations.add("INVALID_ISO17442_LEI: Failed Mod-97-10 check-digit verification for LEI [${leiCode}].")

    if (csdddAuditScore < new BigDecimal("75.0")) complianceViolations.add("CSDDD_AUDIT_SCORE_DEFICIT: Supplier ESG score ${csdddAuditScore} is below mandatory 75.0 threshold.")
    if (criticalFindings > 0) complianceViolations.add("CSDDD_HUMAN_RIGHTS_VIOLATION: Detected ${criticalFindings} unresolved critical findings.")
    try {
        if (LocalDate.parse(auditValidUntilStr).isBefore(LocalDate.now())) {
            complianceViolations.add("CSDDD_AUDIT_EXPIRED: Due diligence audit expired on [${auditValidUntilStr}].")
        }
    } catch (Exception ignored) {
        complianceViolations.add("INVALID_AUDIT_DATE: AuditValidUntil must be a valid YYYY-MM-DD ISO date.")
    }

    if (geoPlots.isEmpty()) {
        complianceViolations.add("EUDR_MISSING_GEOLOCATION: At least one WGS84 GeoJSON production plot is required.")
    } else {
        geoPlots.each { plot ->
            String plotId = (plot.PlotId ?: "UNKNOWN_PLOT").toString()
            List coords = (plot.PolygonCoordinates ?: []) as List
            if (coords.size() < 4) {
                complianceViolations.add("EUDR_INVALID_POLYGON: Plot [${plotId}] must contain at least 4 closed WGS84 coordinate pairs.")
            } else if ((coords.first() as List) != (coords.last() as List)) {
                complianceViolations.add("EUDR_UNCLOSED_POLYGON_RING: Plot [${plotId}] first coordinate must equal last coordinate.")
            }
        }
    }

    String normalizedName = supplierName.toUpperCase().replaceAll("[^A-Z0-9 ]", "")
    String traceabilityHash = MessageDigest.getInstance("SHA-256")
        .digest("${normalizedName}|${leiCode}|${countryCode}|${commodityHsCode}|${geoPlots.size()}".getBytes("UTF-8"))
        .encodeHex().toString()
    message.setHeader("X-MDGS-Traceability-Hash", traceabilityHash)

    if (!complianceViolations.isEmpty()) {
        message.setHeader("X-MDGS-Gate-Status", "BLOCKED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            status: "QUARANTINED_CSDDD_EUDR_HOLD", regulations: ["EU_CSDDD_2024_1760", "EU_EUDR_2023_1115", "ISO_17442_LEI"],
            timestamp: Instant.now().toString(), supplierLegalName: supplierName, lei: leiCode,
            ddsTraceabilitySha256: traceabilityHash, violations: complianceViolations
        ])))
    } else {
        message.setHeader("X-MDGS-Gate-Status", "APPROVED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            "@odata.context": "\$metadata#A_BusinessPartner/\$entity", BusinessPartnerCategory: "2",
            BusinessPartnerFullName: supplierName, BusinessPartnerGrouping: "BP02", LegalEntityIdentifier: leiCode,
            DueDiligenceFingerprint: traceabilityHash,
            to_BusinessPartnerRole: [[BusinessPartnerRole: "FLVN00"], [BusinessPartnerRole: "FLVN01"]],
            to_BusinessPartnerAddress: [[Country: countryCode]],
            EuDueDiligenceStatement_DDS: [DdsReferenceId: "DDS-EU-${traceabilityHash.substring(0, 12).toUpperCase()}", VerifiedAt: Instant.now().toString(), CommodityHsCode: commodityHsCode, CsdddAuditScore: csdddAuditScore, ValidatedGeoPlotCount: geoPlots.size(), ComplianceStatus: "CLEARED_FOR_EU_CUSTOMS_AND_S4HANA"]
        ])))
    }
    return message
}

private boolean isValidIso17442Lei(String lei) {
    if (!lei || !lei.matches("^[A-Z0-9]{20}\$")) return false
    StringBuilder numericLei = new StringBuilder()
    for (char ch : lei.toCharArray()) {
        numericLei.append(Character.isDigit(ch) ? ch : Character.getNumericValue(ch))
    }
    return new BigInteger(numericLei.toString()).mod(BigInteger.valueOf(97)).intValue() == 1
}
