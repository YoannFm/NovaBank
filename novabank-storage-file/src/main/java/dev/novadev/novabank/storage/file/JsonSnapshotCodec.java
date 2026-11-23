package dev.novadev.novabank.storage.file;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instantane JSON par l'API de flux de Gson ({@link JsonReader}, {@link JsonWriter}), presente et
 * stable dans toutes les versions embarquees par les serveurs (2.8.8 a 2.14.0 constatees). Les
 * valeurs sont des textes : aucun nombre n'est converti en {@code double}.
 */
final class JsonSnapshotCodec implements SnapshotCodec {

    @Override
    public String extension() {
        return "json";
    }

    @Override
    public void write(Map<String, Object> tree, Writer output) throws IOException {
        JsonWriter json = new JsonWriter(output);
        json.setIndent("  ");
        writeValue(json, tree);
        json.flush();
    }

    @Override
    public Map<String, Object> read(Reader input) throws IOException {
        JsonReader json = new JsonReader(input);
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IOException(ConsoleText.tr("JSON : objet attendu a la racine"));
        }
        Map<String, Object> tree = readObject(json);
        if (json.peek() != JsonToken.END_DOCUMENT) {
            throw new IOException(ConsoleText.tr("JSON : contenu inattendu apres l'objet racine"));
        }
        return tree;
    }

    private static void writeValue(JsonWriter json, Object value) throws IOException {
        if (value instanceof Map<?, ?> map) {
            json.beginObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                json.name((String) entry.getKey());
                writeValue(json, entry.getValue());
            }
            json.endObject();
        } else if (value instanceof List<?> list) {
            json.beginArray();
            for (Object element : list) {
                writeValue(json, element);
            }
            json.endArray();
        } else {
            json.value((String) value);
        }
    }

    private static Map<String, Object> readObject(JsonReader json) throws IOException {
        Map<String, Object> object = new LinkedHashMap<>();
        json.beginObject();
        while (json.hasNext()) {
            String name = json.nextName();
            if (object.put(name, readValue(json)) != null) {
                throw new IOException(ConsoleText.tr("JSON : cle en double '{0}'", name));
            }
        }
        json.endObject();
        return object;
    }

    private static Object readValue(JsonReader json) throws IOException {
        switch (json.peek()) {
            case BEGIN_OBJECT:
                return readObject(json);
            case BEGIN_ARRAY:
                List<Object> list = new ArrayList<>();
                json.beginArray();
                while (json.hasNext()) {
                    list.add(readValue(json));
                }
                json.endArray();
                return list;
            case STRING:
            case NUMBER:
                // Un nombre saisi a la main est relu en texte exact, jamais en double.
                return json.nextString();
            default:
                throw new IOException(ConsoleText.tr("JSON : valeur inattendue ({0})", json.peek()));
        }
    }
}
