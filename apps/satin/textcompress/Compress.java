// File: $Id$

import java.io.File;

class Compress extends ibis.satin.SatinObject
{
    static final boolean traceMatches = false;
    static final boolean traceLookahead = false;

    private static void generateIndent( java.io.PrintStream str, int n )
    {
        for( int i=0; i<n; i++ ){
            str.print( ' ' );
        }
    }
    // Given a byte array, build an array of backreferences. That is,
    // construct an array that at each text position gives the index
    // of the previous occurence of that hash code, or -1 if there is none.
    private int[] buildBackrefs( byte text[] )
    {
        int heads[] = new int[Configuration.ALPHABET_SIZE];
        int backrefs[] = new int[text.length];

        for( int i=0; i<Configuration.ALPHABET_SIZE; i++ ){
            heads[i] = -1;
        }
        for( int i=0; i<text.length; i++ ){
            int hashcode = (int) text[i];
            backrefs[i] = heads[hashcode];
            heads[hashcode] = i;
        }
        return backrefs;
    }

    /**
     * Returns true iff the given text matches over MINIMAL_SPAN at the
     * two given positions. No explicit bounds checking or overlap
     * checking is done.
     */
    private static boolean matchesMinSpan( byte text[], int p1, int p2 )
    {
        return text[p1] == text[p2] &&
            text[p1+1] == text[p2+1] &&
            text[p1+2] == text[p2+2] &&
            text[p1+3] == text[p2+3];
    }

    private static int[] collectBackrefs( byte text[], int backrefs[], int pos )
    {
        // First count the number of backreferences.
        int n = 0;

        int backpos = backrefs[pos];
        while( backpos>=0 ){
            if( backpos<pos-Configuration.MINIMAL_SPAN ){
                if( matchesMinSpan( text, backpos, pos ) ){
                    // This is a sensible backref.
                    n++;
                }
            }
            backpos = backrefs[backpos];
        }

        // And now build an array with them.
        int res[] = new int[n];
        backpos = backrefs[pos];
        n = 0;
        while( backpos>=0 ){
            if( backpos<pos-Configuration.MINIMAL_SPAN ){
                if( matchesMinSpan( text, backpos, pos ) ){
                    res[n++] = backpos;
                }
            }
            backpos = backrefs[backpos];
        }
        return res;
    }

    /**
     * Given a list of backreferences and a match length, return an
     * adapted version of the nearest (and hence cheapest) backreference that
     * covers this match length.
     */
    Backref buildBestMove( Backref results[] , int n )
    {
        Backref r = null;

        for( int i=0; i<results.length; i++ ){
            Backref b = results[i];

            if( b != null && b.len>=n ){
                if( r == null || r.backpos<b.backpos ){
                    r = b;
                }
            }
        }
        return new Backref( r.backpos, r.pos, n );
    }

    public Backref shallowEvaluateBackref( final byte text[], final int backrefs[], int backpos, int pos )
    {
        Backref r;

        int len = Helpers.matchSpans( text, backpos, pos );
        if( len >= Configuration.MINIMAL_SPAN ){
            r = new Backref( backpos, pos, len );

            if( traceMatches ){
                System.out.println( "A match " + r );
            }
        }
        else {
            r = null;
        }
        return r;
    }

    /**
     * @param text The text to compress.
     * @param backrefs The index of the first previous occurence of this hash.
     * @param pos The position to select the move for.
     * @param bestpos First uncompressed byte by the best move known to parent.
     * @param depth The recursion depth of this selection.
     * @return The best move, or null if we can't better bestpos.
     */
    public Backref selectBestMove( byte text[], int backrefs[], int pos, int bestpos, int depth )
    {
        Backref mv = null;
        boolean haveAlternatives = false;
        int maxLen = 0;
        int minLen = text.length;

        if( pos+1>=bestpos ){
            // A simple character copy is worth considering.
            mv = Backref.buildCopyBackref( pos );
        }
        if( pos+Configuration.MINIMAL_SPAN>=text.length ){
            return mv;
        }
        if( traceLookahead ){
            generateIndent( System.out, depth );
            System.out.println( "D" +  depth + ": @" + pos + ": selecting best move improving on @" + bestpos );
        }
        int sites[] = collectBackrefs( text, backrefs, pos );

        Backref results[] = new Backref[Magic.MAX_COST+1];
        for( int i=0; i<sites.length; i++ ){
            Backref r = shallowEvaluateBackref( text, backrefs, sites[i], pos );

            if( r != null ){
                int cost = r.getCost();

                // If this backreference is worth the extra trouble,
                // register it.
                if( pos+r.len>bestpos-cost ){
                    if( results[cost] == null || results[cost].len<r.len ){
                        results[cost] = r;
                        haveAlternatives = true;
                        if( maxLen<r.len ){
                            maxLen = r.len;
                        }
                        int minl = 1+cost;
                        if( minl<minLen ){
                            minLen = minl;
                        }
                    }
                }
            }
        }

        if( !haveAlternatives ){
            // The only possible move is a copy.
            if( mv != null && depth>0 && depth<Configuration.LOOKAHEAD_DEPTH ){
                // It is permitted and useful to evaluate the copy move
                // using recursion, so that higher levels can accurately
                // compare it to other alternatives.
                // Evaluate the gain of just copying the character.
                Backref mv1 = selectBestMove( text, backrefs, pos+1, pos+1, depth+1 );
                // TODO: See if we can get rid of this sync.
                sync();
                mv.addGain( mv1 );
            }
            if( traceLookahead ){
                generateIndent( System.out, depth );
                System.out.println( "D" + depth + ": no backrefs, so only move is: " + mv );
            }
            return mv;
        }

        if( traceLookahead ){
            if( mv != null ){
                generateIndent( System.out, depth );
                System.out.println( "D" + depth + ":  considering move " + mv );
            }
            for( int c=0; c<results.length; c++ ){
                Backref r = results[c];
                if( r != null ){
                    generateIndent( System.out, depth );
                    System.out.println( "D" + depth + ":  considering move " + r );
                }
            }
        }

        if( depth<Configuration.LOOKAHEAD_DEPTH ){
            Backref mv1 = null;

            // We have some recursion depth left. We know we can backreference
            // a span of at most maxLen characters. In recursion see if it
            // is worthwile to shorten this to allow a longer subsequent
            // match.
            if( minLen<Configuration.MINIMAL_SPAN ){
                minLen = Configuration.MINIMAL_SPAN;
            }
            if( minLen<maxLen-Configuration.MAX_SHORTENING ){
                minLen = maxLen-Configuration.MAX_SHORTENING;
            }
            if( pos+minLen<=bestpos ){
                minLen = 1+bestpos-pos;
            }

            Backref a[] = new Backref[maxLen+1];
            if( traceLookahead ){
                generateIndent( System.out, depth );
                System.out.println( "D" + depth + ": @" + pos + ": evaluating backreferences of " + minLen + "..." + maxLen + " bytes" );
            }

            // Spawn recurrent processes to evaluate a character copy
            // and backreferences of a range of lengths.
            if( mv != null ){
                mv1 = selectBestMove( text, backrefs, pos+1, pos+1, depth+1 );
            }
            for( int i=minLen; i<=maxLen; i++ ){
                a[i] = selectBestMove( text, backrefs, pos+i, pos+maxLen, depth+1 );
            }
            sync();
            int bestGain = -1;
            if( mv != null ){
                bestGain = mv1.getGain();
                mv.addGain( bestGain );
            }
            for( int i=minLen; i<=maxLen; i++ ){
                Backref r = a[i];

                if( r != null ){
                    Backref mymv = buildBestMove( results, i );
                    mymv.addGain( r );
                    int g = mymv.getGain();

                    if( g>bestGain ){
                        mv = mymv;
                        bestGain = g;
                    }
                }
            }
        }
        else {
            // We're at the end of recursion. Simply pick the match
            // with the best gain.
            int bestGain = 0;

            for( int i=0; i<results.length; i++ ){
                Backref r = results[i];

                if( r != null ){
                    int g = r.getGain();

                    if( g>bestGain ){
                        mv = r;
                        bestGain = g;
                    }
                }
            }
        }
        if( traceLookahead ){
            generateIndent( System.out, depth );
            System.out.println( "D" + depth + ": best move is: " + mv );
        }
        return mv;
    }

    public ByteBuffer compress( byte text[] )
    {
        int backrefs[] = buildBackrefs( text );
        int pos = 0;
        ByteBuffer out = new ByteBuffer();

        while( pos<Configuration.MINIMAL_SPAN && pos<text.length ){
            out.append( text[pos++] );
        }
        while( pos+Configuration.MINIMAL_SPAN<text.length ){
            Backref mv = selectBestMove( text, backrefs, pos, pos, 0 );
            sync();
            if( mv.backpos<0 ){
                // There is no backreference that gives any gain, so
                // just copy the character.
                out.append( text[pos++] );
            }
            else {
                // There is a backreference that helps, write it to
                // the output stream.
                out.appendRef( pos, mv );

                // And skip all the characters that we've backreferenced.
                pos += mv.len;
            }
        }

        // Write the last few characters without trying to compress.
        while( pos<text.length ){
            out.append( text[pos++] );
        }
        return out;
    }

    /**
     * Allows execution of the class.
     * @param args The command-line arguments.
     */
    public static void main( String args[] ) throws java.io.IOException
    {
	if( args.length != 2 ){
	    System.err.println( "Usage: <text> <compressedtext>" );
	    System.exit( 1 );
	}
	File infile = new File( args[0] );
	File outfile = new File( args[1] );
        byte text[] = Helpers.readFile( infile );
	long startTime = System.currentTimeMillis();

        System.out.println( "Recursion depth: " + Configuration.LOOKAHEAD_DEPTH + ", max. shortening: " + Configuration.MAX_SHORTENING  );

        Compress c = new Compress();

        ByteBuffer buf = c.compress( text );

        Helpers.writeFile( outfile, buf );

	long endTime = System.currentTimeMillis();
	double time = ((double) (endTime - startTime))/1000.0;

	System.out.println( "ExecutionTime: " + time );
        System.out.println( "In: " + text.length + " bytes, out: " + buf.sz + " bytes." );
    }
}
