package com.prideuniversal.config;

import com.prideuniversal.common.CodedEnum;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds {@code ?status=open} to a {@link CodedEnum} by its code. Without this,
 * Spring binds enums by name and every lowercase filter the app sends is a 400.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Public policy page Google Play links to (Child safety standards). */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/child-safety").setViewName("forward:/child-safety.html");
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CodedEnumConverterFactory());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class CodedEnumConverterFactory implements ConverterFactory<String, CodedEnum> {
        @Override
        public <T extends CodedEnum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> source == null || source.isBlank()
                    ? null
                    : (T) CodedEnum.fromCode((Class) targetType, source);
        }
    }
}
