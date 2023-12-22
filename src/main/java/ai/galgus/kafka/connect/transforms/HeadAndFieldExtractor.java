package ai.galgus.kafka.connect.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.header.Headers;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

public class HeadAndFieldExtractor<R extends ConnectRecord<R>> implements Transformation<R> {
  private static final Logger log = LoggerFactory.getLogger(HeadAndFieldExtractor.class);

  public static final String FIELD_CONF = "headerField";
  public static final String FIELD_DOC = "Field name in the record whose values are to be moved to headers.";
  public static final String HEADER_CONF = "headerName";
  public static final String HEADER_DOC = "The destiny header name";
  public static final String EXTRACTED_FIELD_CONF = "extractedField";
  public static final String EXTRACTED_FIELD_DOC = "The field to extract as byte array";

  private String headerField;
  private String headerName;
  private String extratedField;

  @Override public ConfigDef config() {
    return CONFIG_DEF;
  }

  public static final ConfigDef CONFIG_DEF = new ConfigDef()
    .define(FIELD_CONF, ConfigDef.Type.STRING,
      ConfigDef.NO_DEFAULT_VALUE, new ConfigDef.NonEmptyString(),
      ConfigDef.Importance.HIGH, FIELD_DOC)
    .define(HEADER_CONF, ConfigDef.Type.STRING,
      ConfigDef.NO_DEFAULT_VALUE, new ConfigDef.NonEmptyString(),
      ConfigDef.Importance.HIGH, HEADER_DOC)
    .define(EXTRACTED_FIELD_CONF, ConfigDef.Type.STRING,
      ConfigDef.NO_DEFAULT_VALUE, new ConfigDef.NonEmptyString(),
      ConfigDef.Importance.HIGH, EXTRACTED_FIELD_DOC);

  @Override public void configure(Map<String, ?> props) {
    log.info("Configuring transformer");

    final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);

    headerField = config.getString(FIELD_CONF);
    log.debug("headerField: {}", headerField);

    headerName = config.getString(HEADER_CONF);
    log.debug("headerName: {}", headerName);

    extratedField = config.getString(EXTRACTED_FIELD_CONF);
    log.debug("extractedField: {}", extratedField);

    log.info("Configuration done!");
  }

  @Override public R apply(R originalRecord) {
    // If record value isn't instance of Map then return original record
    if (!(originalRecord.value() instanceof Map))
      return originalRecord;

    Map<String, Object> valueMap = (Map<String, Object>)originalRecord.value();

    // If record value doesn't contain the field to use as header or the field to extract then return original record
    if (!valueMap.containsKey(headerField) || !valueMap.containsKey(extratedField))
      return originalRecord;

    Object valueFieldContent = valueMap.remove(headerField);

    // If the value's field content isn't instance of Map then return original record
    if (!(valueFieldContent instanceof Map))
      return originalRecord;

    // Transform the content into a string of comma separated values with format <key>:<value>
    String headers = ((Map<String, Object>) valueFieldContent)
      .entrySet()
      .stream()
      .map(entry -> (entry.getKey() + ":" + entry.getValue()))
      .collect(Collectors.joining(","));

    // Duplicate record headers
    Headers recordHeaders = originalRecord.headers().duplicate();
    recordHeaders.addString(headerName, headers);

    Object extractedFieldContent = valueMap.remove(extratedField);

    // If the extracted field content isn't a string return original record
    if (!(extractedFieldContent instanceof String))
      return originalRecord;

    return originalRecord.newRecord(
      originalRecord.topic(), originalRecord.kafkaPartition(),
      originalRecord.keySchema(), originalRecord.key(),
      Schema.BYTES_SCHEMA, ((String) extractedFieldContent).getBytes(),
      originalRecord.timestamp(), recordHeaders);
  }


  @Override public void close() {
    // Nothing to do
  }

}
