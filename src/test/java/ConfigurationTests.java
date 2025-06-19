import com.github.sinakarimi81.jdown.configuration.ConfigurationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ConfigurationTests {

    @Test
    void populateConfigs_WhenFileExistsPopulateCorrectly() {
        assertDoesNotThrow(() -> ConfigurationUtils.populateConfigs(false));
        assertTrue(ConfigurationUtils.hasConfigs());
    }

    @Test
    void WhenGetConfigAndConfigsNull_ThenPopulateConfigs() {
        assertDoesNotThrow(() -> ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS));
        assertTrue(ConfigurationUtils.hasConfigs());
    }

    @Test
    void getConfigs_WhenConfigKeyExists() {
        Object config = ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS);
        assertNotNull(config);
        assertInstanceOf(Integer.class, config);
    }

    @Test
    void getConfigs_WhenConfigKeyExistsAndTypeIsGiven() {
        Integer numberOfThreads = ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS, Integer.class);
        assertNotNull(numberOfThreads);
    }

    @Test
    void getConfigs_WhenConfigKeyExistsAndTypeDoesNotMatch() {
        assertThrows(ClassCastException.class, () -> ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS, String.class));
    }

    @Test
    void getConfigs_WhenConfigKeyExistsAndTypeisNotGiven() {
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS, null));
    }

    @Test
    void getConfigs_WhenConfigKeyNotExists() {
        Object config = ConfigurationUtils.getConfig("SomeKey");
        assertNull(config);
    }

}
