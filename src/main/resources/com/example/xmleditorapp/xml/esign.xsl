<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="http://www.w3.org/2000/svg">
<xsl:output method="xml" indent="yes" encoding="UTF-8"/>

<xsl:variable name="INFO_HEIGHT" select="100"/>
<xsl:variable name="PADDING" select="10"/>
<xsl:variable name="TEXT_X" select="20"/>

<xsl:template match="/">
    <xsl:variable name="node-width" select="number(/*/@width)"/>
    <xsl:variable name="node-height" select="number(/*/@height)"/>
    
    <xsl:variable name="TOTAL_HEIGHT" select="$node-height + $INFO_HEIGHT + $PADDING * 2"/>
    <xsl:variable name="TOTAL_WIDTH" select="$node-width + $PADDING * 2"/>

    <svg version="1.1" width="{$TOTAL_WIDTH}" height="{$TOTAL_HEIGHT}" viewBox="0 0 {$TOTAL_WIDTH} {$TOTAL_HEIGHT}">
        <title>Visualization of <xsl:value-of select="name(/*)"/></title>

        <text x="{$TEXT_X}" y="30" font-size="16" font-weight="bold" fill="black">
            Selected Node: <xsl:value-of select="name(/*)"/>
        </text>
        
        <xsl:if test="/*/@name">
            <text x="{$TEXT_X}" y="50" font-size="14" fill="blue">
                Name: <xsl:value-of select="/*/@name"/>
            </text>
        </xsl:if>
        
        <text x="{$TEXT_X}" y="70" font-size="12" fill="red">
            Attributes:
        </text>
        
        <xsl:for-each select="/*/@*">
            <text x="{$TEXT_X + 10}" y="{85 + position() * 15}" font-size="10" fill="red">
                <xsl:value-of select="name()"/>="<xsl:value-of select="."/>"
            </text>
        </xsl:for-each>

        <xsl:variable name="RECT_Y_START" select="$INFO_HEIGHT + $PADDING"/>
        
        <rect x="{$PADDING}" y="{$RECT_Y_START}" 
              width="{$node-width}" height="{$node-height}"
              style="fill:lightgray;stroke:black;stroke-width:2;"/>
        
        <xsl:apply-templates select="/*/*[@x and @y and @width and @height]" mode="child-rects">
            <xsl:with-param name="absolute-origin-x" select="$PADDING"/>
            <xsl:with-param name="absolute-origin-y" select="$RECT_Y_START"/>
        </xsl:apply-templates>

    </svg>
</xsl:template>

---

<xsl:template match="*" mode="child-rects">
    <xsl:param name="absolute-origin-x" select="0"/>
    <xsl:param name="absolute-origin-y" select="0"/>
    
    <xsl:variable name="x-pos" select="number(@x) + $absolute-origin-x"/>
    <xsl:variable name="y-pos" select="number(@y) + $absolute-origin-y"/>

    <g class="child-node">
        <rect x="{$x-pos}" y="{$y-pos}" 
              width="{number(@width)}" height="{number(@height)}"
              style="fill:#B0C4DE; stroke:#4682B4; stroke-width:2; opacity:0.8;"/>

        <text x="{$x-pos + 5}" y="{$y-pos + 15}" font-size="10" fill="black">
            <xsl:value-of select="name()"/>: <xsl:value-of select="@name"/>
        </text>
    </g>
    
    </xsl:template>

<xsl:template match="*"/>
<xsl:template match="@*"/>

</xsl:stylesheet>