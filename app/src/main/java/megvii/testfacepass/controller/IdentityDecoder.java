package megvii.testfacepass.controller;

import org.bouncycastle.asn1.*;
import java.util.*;
import java.util.Base64;

public class IdentityDecoder {
    public static Map<Integer, String> parse() throws Exception {
        String base64 = "bYIBlDCCAZACAQEGBijTFgEACDGCAYEwEQIBARMMMDI3MjAzMDAwMzA3" +
                        "MBwCAQIMF05ndXnhu4VuIMSQw6xuaCDDmnQgQml1MA8CAQMTCjI3LzAz" +
                        "LzIwMDMwCAIBBAwDTmFtMA8CAQUMClZp4buHdCBOYW0wCQIBBgwES2lu" +
                        "aDALAgEHDAZLaMO0bmcwJwIBCAwiTMOjbmcgTmfDom0sIEdpYSBCw6xu" +
                        "aCwgQuG6r2MgTmluaDA8AgEJDDdUaMO0biBOZ8SDbSBMxrDGoW5nLCBM" +
                        "w6NuZyBOZ8OibSwgR2lhIELDrG5oLCBC4bqvYyBOaW5oMC8CAQoMKk7h" +
                        "u5F0IHJ14buTaSBDOiAxY20gZMaw4bubaSBzYXUgbcOpcCB0csOhaTAP" +
                        "AgELEwoyNC8wOC8yMDIyMA8CAQwMCjI3LzAzLzIwMjgwLwIBDTAYDBZO" +
                        "Z3V54buFbiDEkMOsbmggSHV5w6puMBAMDk5nw7QgVGjhu4sgVGh1MAMC" +
                        "AQ4wAwIBDzAVAgEQExAwMjk0MDExRDI1MzQwMDAw";
        byte[] data = Base64.getDecoder().decode(base64);
        ASN1Primitive root = ASN1Primitive.fromByteArray(data);
        Map<Integer, String> fields = new HashMap<>();
        collectFields(root, fields);

        return fields;
    }

    private static void collectFields(ASN1Primitive obj, Map<Integer, String> out) {
        if (obj instanceof ASN1TaggedObject) {
            collectFields(((ASN1TaggedObject) obj)
                    .getBaseObject().toASN1Primitive(), out);
            return;
        }

        if (obj instanceof ASN1Sequence) {
            ASN1Sequence seq = (ASN1Sequence) obj;

            // Field ::= SEQUENCE { INTEGER, value }
            if (seq.size() >= 1 && seq.getObjectAt(0) instanceof ASN1Integer) {

                int id = ((ASN1Integer) seq.getObjectAt(0))
                        .getValue().intValue();

                String value = null;

                if (seq.size() > 1 && seq.getObjectAt(1) instanceof ASN1String) {
                    String v = ((ASN1String) seq.getObjectAt(1)).getString();
                    value = v.isEmpty() ? null : v;
                }

                out.put(id, value);
            }

            // Continue traversal
            Enumeration<?> e = seq.getObjects();
            while (e.hasMoreElements()) {
                ASN1Encodable enc = (ASN1Encodable) e.nextElement();
                collectFields(enc.toASN1Primitive(), out);
            }
            return;
        }

        if (obj instanceof ASN1Set) {
            Enumeration<?> e = ((ASN1Set) obj).getObjects();
            while (e.hasMoreElements()) {
                ASN1Encodable enc = (ASN1Encodable) e.nextElement();
                collectFields(enc.toASN1Primitive(), out);
            }
        }
    }
}
