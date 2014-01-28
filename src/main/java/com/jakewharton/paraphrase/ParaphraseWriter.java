package com.jakewharton.paraphrase;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.io.Closeables;
import com.squareup.javawriter.JavaWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class ParaphraseWriter {
  private static final String PHRASE_CLASS = "Phrase";
  private static final String ABSTRACT_PHRASE_CLASS = "AbstractPhrase";

  private final File outputDir;

  ParaphraseWriter(File outputDir) {
    this.outputDir = outputDir;
  }

  void write(String packageName, List<Phrase> phrases) throws IOException {
    String filePath = packageName.replace('.', File.separatorChar) + "/" + PHRASE_CLASS + ".java";

    File file = new File(outputDir, filePath);
    file.getParentFile().mkdirs();

    FileWriter fileWriter = new FileWriter(file);
    JavaWriter writer = new JavaWriter(fileWriter);
    try {
      writer.emitSingleLineComment("Generated by Paraphrase plugin. Do not modify!");
      writer.emitPackage(packageName);

      writer.emitImports("android.content.Context");
      writer.emitImports("android.content.res.Resources");
      writer.emitImports("android.view.View");
      writer.emitEmptyLine();

      writer.beginType(PHRASE_CLASS, "class", EnumSet.of(PUBLIC, FINAL));

      // Factory method for each phrase.
      for (Phrase phrase : phrases) {
        String className = classNameOf(phrase);

        if (phrase.documentation != null) {
          writer.emitJavadoc(phrase.documentation);
        }
        writer.beginMethod(className, phrase.name, EnumSet.of(PUBLIC, STATIC));
        writer.emitStatement("return new %s()", className);
        writer.endMethod();
        writer.emitEmptyLine();
      }

      for (Phrase phrase : phrases) {
        writePhraseClass(writer, phrase);
        writer.emitEmptyLine();
      }

      writeAbstractPhraseClass(writer);
      writer.emitEmptyLine();

      writer.beginConstructor(EnumSet.of(PRIVATE));
      writer.emitStatement("throw new AssertionError(\"No instances.\")");
      writer.endConstructor();

      writer.endType();
    } finally {
      Closeables.close(writer, true);
    }
  }

  private static void writeAbstractPhraseClass(JavaWriter writer) throws IOException {
    writer.beginType(ABSTRACT_PHRASE_CLASS, "class", EnumSet.of(PUBLIC, STATIC, ABSTRACT));

    writer.emitField("String[]", "keys", EnumSet.of(PRIVATE, FINAL));
    writer.emitField("String[]", "values", EnumSet.of(FINAL));
    writer.emitEmptyLine();

    writer.beginConstructor(EnumSet.of(PRIVATE), "String...", "keys");
    writer.emitStatement("this.keys = keys");
    writer.emitStatement("this.values = new String[keys.length]");
    writer.endConstructor();
    writer.emitEmptyLine();

    writer.beginMethod("String", "build", EnumSet.of(PUBLIC, FINAL), "View", "view");
    writer.emitStatement("return build(view.getContext().getResources())");
    writer.endMethod();
    writer.emitEmptyLine();

    writer.beginMethod("String", "build", EnumSet.of(PUBLIC, FINAL), "Context", "context");
    writer.emitStatement("return build(context.getResources())");
    writer.endMethod();
    writer.emitEmptyLine();

    writer.beginMethod("String", "build", EnumSet.of(PUBLIC, FINAL), "Resources", "res");
    writer.emitStatement("return \"\"");
    // TODO do actual replacement
    writer.endMethod();

    writer.endType();
  }

  static void writePhraseClass(JavaWriter writer, Phrase phrase) throws IOException {
    String className = classNameOf(phrase);
    writer.beginType(className, "class", EnumSet.of(PUBLIC, STATIC, FINAL), ABSTRACT_PHRASE_CLASS);

    // Empty, no-arg constructor.
    writer.beginConstructor(EnumSet.of(PRIVATE));
    writer.emitStatement("super(%s)", Joiner.on(", ")
        .join(FluentIterable.from(phrase.tokens).transform(new Function<String, String>() {
          @Override public String apply(String token) {
            return "\"" + token + "\"";
          }
        })));
    writer.endConstructor();
    writer.emitEmptyLine();

    List<String> tokens = phrase.tokens;
    for (int i = 0, count = tokens.size(); i < count; i++) {
      String tokenName = tokens.get(i);
      writer.beginMethod(className, tokenName, EnumSet.of(PUBLIC), "String", tokenName);
      writer.emitStatement("values[%s] = %s", i, tokenName);
      writer.emitStatement("return this");
      writer.endMethod();

      if (i < count - 1) {
        writer.emitEmptyLine();
      }
    }

    writer.endType();
  }

  private static String classNameOf(Phrase phrase) {
    return "Phrase_" + phrase.name;
  }
}
