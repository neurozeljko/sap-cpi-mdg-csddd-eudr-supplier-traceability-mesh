import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant

def Message processData(Message message) {
    def ex = message.getProperties().get("CamelExceptionCaught")
    message.setHeader("CamelHttpResponseCode", 500)
    message.setHeader("Content-Type", "application/json")
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
        error: "MDGS_CSDDD_EUDR_PIPELINE_ERROR", timestamp: Instant.now().toString(),
        details: ex ? ex.getMessage() : "Unhandled MDG-S Supplier Due Diligence Failure"
    ])))
    return message
}
