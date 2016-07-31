package com.ugcs.mavlink.generator;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public final class JavaNames {
	private static final Set<String> JAVA_KEYWORDS = newJavaKeywords();
	
	private static Set<String> newJavaKeywords() {
		Set<String> javaKeywords = new HashSet<String>();
		javaKeywords.add("abstract");
		javaKeywords.add("continue");
		javaKeywords.add("for");
		javaKeywords.add("new");
		javaKeywords.add("switch");
		javaKeywords.add("assert");
		javaKeywords.add("default");
		javaKeywords.add("goto");
		javaKeywords.add("package");
		javaKeywords.add("synchronized");
		javaKeywords.add("boolean");
		javaKeywords.add("do");
		javaKeywords.add("if");
		javaKeywords.add("private");
		javaKeywords.add("this");
		javaKeywords.add("break");
		javaKeywords.add("double");
		javaKeywords.add("implements");
		javaKeywords.add("protected");
		javaKeywords.add("throw");
		javaKeywords.add("byte");
		javaKeywords.add("else");
		javaKeywords.add("import");
		javaKeywords.add("public");
		javaKeywords.add("throws");
		javaKeywords.add("case");
		javaKeywords.add("enum");
		javaKeywords.add("instanceof");
		javaKeywords.add("return");
		javaKeywords.add("transient");
		javaKeywords.add("catch");
		javaKeywords.add("extends");
		javaKeywords.add("int");
		javaKeywords.add("short");
		javaKeywords.add("try");
		javaKeywords.add("char");
		javaKeywords.add("final");
		javaKeywords.add("interface");
		javaKeywords.add("static");
		javaKeywords.add("void");
		javaKeywords.add("class");
		javaKeywords.add("finally");
		javaKeywords.add("long");
		javaKeywords.add("strictfp");
		javaKeywords.add("volatile");
		javaKeywords.add("const");
		javaKeywords.add("float");
		javaKeywords.add("native");
		javaKeywords.add("super");
		javaKeywords.add("while");
		return Collections.unmodifiableSet(javaKeywords);
	}
	
	private JavaNames() {
	}
	
	public static String[] split(String source) {
		List<String> result = new LinkedList<String>();
		int tokenStartPosition = 0;
		boolean tokenOpened = false;
		main:
		for (int i = 0; i < source.length(); ++i) {
			char c = source.charAt(i);
			boolean matched = false;
			boolean skipChar = false;

			if (i > 0
					&& Character.isUpperCase(c)
					&& Character.isLowerCase(source.charAt(i - 1))) {
				matched = true;
				skipChar = false;
			}
			if (c == '_') {
				matched = true;
				skipChar = true;
			}
			if (matched && tokenOpened) {
				String value = source.substring(tokenStartPosition, i);
				if (value.length() > 0) {
					result.add(value);
					tokenOpened = false;
				}
			}
			if (skipChar)
				continue main;
			
			if (!tokenOpened) {
				tokenOpened = true;
				tokenStartPosition = i;
			}
		}
		if (tokenOpened) {
			String value = source.substring(tokenStartPosition);
			if (value.length() > 0) {
				result.add(value);
			}
		}
		return result.toArray(new String[result.size()]);
	}
	
	public static String escape(String name) {
		if (JAVA_KEYWORDS.contains(name))
			return "_" + name;
		return name;
	}
	
	public static String toTitleCase(String str) {
		if (str == null)
			return null;
		if (str.length() < 2) // empty string case is here
			return str.toUpperCase();
		
		return Character.toUpperCase(str.charAt(0))
				+ str.substring(1).toLowerCase();
	}
	
	public static String toClassName(String[] tokens) {
		StringBuilder sb = new StringBuilder();
		for (String token : tokens) {
			if (token == null || token.isEmpty())
				continue;
			sb.append(toTitleCase(token));
		}
		return sb.toString();
	}
	
	public static String toClassName(String name) {
		return toClassName(split(name));
	}
	
	public static String toFieldName(String[] tokens) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		
		for (String token : tokens) {
			if (token == null || token.isEmpty())
				continue;
			if (first) {
				sb.append(token.toLowerCase());
				first = false;
			} else {
				sb.append(toTitleCase(token));
			}
		}
		return escape(sb.toString());
	}
	
	public static String toFieldName(String name) {
		return toFieldName(split(name));
	}
	
	public static String toConstantName(String[] tokens) {
		StringBuilder sb = new StringBuilder();
		for (String token : tokens) {
			if (token == null || token.isEmpty())
				continue;
			if (sb.length() > 0)
				sb.append("_");
			sb.append(token.toUpperCase());
		}
		return sb.toString();
	}
	
	public static String toConstantName(String name) {
		return toConstantName(split(name));
	}
}
