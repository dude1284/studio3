package com.aptana.editor.ruby.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.aptana.editor.ruby.RubyEditorTest;
import com.aptana.editor.ruby.RubyParserTest;
import com.aptana.editor.ruby.RubyRegexScannerTest;
import com.aptana.editor.ruby.RubySourcePartitionScannerTest;
import com.aptana.editor.ruby.RubyTokenScannerTest;

public class AllTests
{

	public static Test suite()
	{
		TestSuite suite = new TestSuite("Test for com.aptana.editor.ruby.tests");
		// $JUnit-BEGIN$
		suite.addTestSuite(RubySourcePartitionScannerTest.class);
		suite.addTestSuite(RubyTokenScannerTest.class);
		suite.addTestSuite(RubyRegexScannerTest.class);
		suite.addTestSuite(RubyParserTest.class);
		suite.addTestSuite(RubyEditorTest.class);
		// $JUnit-END$
		return suite;
	}
}
