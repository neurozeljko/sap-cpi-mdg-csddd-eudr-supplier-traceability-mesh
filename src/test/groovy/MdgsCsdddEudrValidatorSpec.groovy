import spock.lang.Specification
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.sap.gateway.ip.core.customdev.util.Message

class MdgsCsdddEudrValidatorSpec extends Specification {
    def script = new GroovyShell().parse(new File("src/main/groovy/MDGS_CSDDD_EUDR_Governance_Engine.groovy"))

    def "Should certify compliant supplier with valid ISO 17442 LEI, CSDDD audit, and closed EUDR polygon"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            SupplierLegalName: "Rheinische Agrar GmbH", Country: "DE", LEI: "529900T8BM49AURSDO55",
            CommodityHsCode: "18010000", CsdddAuditScore: 91.5, CriticalHumanRightsFindings: 0, AuditValidUntil: "2027-06-30",
            EudrGeoPlots: [[PlotId: "P1", PolygonCoordinates: [[-48.49, -1.45], [-48.48, -1.45], [-48.48, -1.46], [-48.49, -1.45]]]]
        ]))
        when:
        def res = script.processData(msg)
        def json = new JsonSlurper().parseText(res.getBody(String))
        then:
        res.getHeaders()["X-MDGS-Gate-Status"] == "APPROVED"
        json.EuDueDiligenceStatement_DDS.ValidatedGeoPlotCount == 1
    }

    def "Should block supplier failing ISO 17442 LEI Mod-97-10 check-digit"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            SupplierLegalName: "Invalid LEI Corp", Country: "DE", LEI: "529900T8BM49AURSDO99",
            CsdddAuditScore: 90.0, CriticalHumanRightsFindings: 0, AuditValidUntil: "2027-06-30",
            EudrGeoPlots: [[PlotId: "P1", PolygonCoordinates: [[-48.49, -1.45], [-48.48, -1.45], [-48.48, -1.46], [-48.49, -1.45]]]]
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDGS-Gate-Status"] == "BLOCKED"
    }

    def "Should block supplier with unclosed EUDR WGS84 GeoJSON polygon ring"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([
            SupplierLegalName: "Open Ring Timber Ltd", Country: "BR", LEI: "529900T8BM49AURSDO55",
            CsdddAuditScore: 85.0, CriticalHumanRightsFindings: 0, AuditValidUntil: "2027-06-30",
            EudrGeoPlots: [[PlotId: "P2", PolygonCoordinates: [[-54.12, -3.21], [-54.11, -3.21], [-54.11, -3.22], [-54.15, -3.25]]]]
        ]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDGS-Gate-Status"] == "BLOCKED"
    }
}
