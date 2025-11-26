<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns="http://www.w3.org/2000/svg">
    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <xsl:variable name="DEFAULT_CANVAS_DIM" select="500"/>
    <xsl:variable name="DEFAULT_CHILD_DIM" select="10"/>

    <xsl:variable name="PADDING" select="10"/>
    <xsl:variable name="TEXT_X" select="20"/>
    <xsl:variable name="LINE_HEIGHT" select="15"/>

    <xsl:template match="/">
        <xsl:variable name="node-width">
            <xsl:choose>
                <xsl:when test="number(/*/@width) &gt; 0">
                    <xsl:value-of select="number(/*/@width)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$DEFAULT_CANVAS_DIM"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="node-height">
            <xsl:choose>
                <xsl:when test="number(/*/@height) &gt; 0">
                    <xsl:value-of select="number(/*/@height)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$DEFAULT_CANVAS_DIM"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="attribute-count" select="count(/*/@*)"/>
        <xsl:variable name="INFO_HEIGHT_CALCULATED" select="(3 * $LINE_HEIGHT) + ($attribute-count * $LINE_HEIGHT) + (2 * $PADDING)"/>

        <xsl:variable name="TOTAL_HEIGHT" select="$node-height + $INFO_HEIGHT_CALCULATED + $PADDING"/>
        <xsl:variable name="TOTAL_WIDTH" select="$node-width + $PADDING * 2"/>

        <svg version="1.1" width="{$TOTAL_WIDTH}" height="{$TOTAL_HEIGHT}" viewBox="0 0 {$TOTAL_WIDTH} {$TOTAL_HEIGHT}">
            <title>Visualization of <xsl:value-of select="name(/*)"/></title>

            <script type="text/ecmascript">
                <![CDATA[
                var selectedElement = null;
                var offset = {x: 0, y: 0};
                var svg = document.querySelector('svg');

                function getMousePosition(evt) {
                    var CTM = svg.getScreenCTM();
                    return {
                        x: (evt.clientX - CTM.e) / CTM.a,
                        y: (evt.clientY - CTM.f) / CTM.d
                    };
                }

                function startDrag(evt) {
                    if (evt.target.parentNode.classList.contains('child-node')) {
                        selectedElement = evt.target.parentNode;
                    } else if (evt.target.classList.contains('child-node')) {
                        selectedElement = evt.target;
                    } else {
                        return;
                    }

                    // Do not drag if the click was part of a specific edit interaction
                    if (evt.target.tagName === 'text') return;

                    var coord = getMousePosition(evt);

                    var transform = selectedElement.transform.baseVal.getItem(0).matrix;
                    var dx = transform.e;
                    var dy = transform.f;

                    offset.x = coord.x - dx;
                    offset.y = coord.y - dy;

                    svg.addEventListener('mousemove', drag);
                    svg.addEventListener('mouseup', endDrag);

                    // Bring to front
                    svg.appendChild(selectedElement);
                }

                function drag(evt) {
                    if (selectedElement) {
                        evt.preventDefault();
                        var coord = getMousePosition(evt);
                        var newX = coord.x - offset.x;
                        var newY = coord.y - offset.y;

                        selectedElement.transform.baseVal.getItem(0).setTranslate(newX, newY);
                    }
                }

                function endDrag(evt) {
                    if (!selectedElement) return;

                    // 1. Capture the New Coordinates (for drag action only)
                    var nodeName = selectedElement.getAttribute('data-node-name');
                    var originalX = parseFloat(selectedElement.getAttribute('data-original-x'));
                    var originalY = parseFloat(selectedElement.getAttribute('data-original-y'));

                    var transform = selectedElement.transform.baseVal.getItem(0).matrix;
                    var translateX = transform.e;
                    var translateY = transform.f;

                    // Calculate the new X and Y based on original XML coordinates
                    var newX = Math.round(originalX + translateX);
                    var newY = Math.round(originalY + translateY);

                    // If the position changed significantly, send update
                    if (Math.abs(translateX) > 1 || Math.abs(translateY) > 1) {
                        var updateData = {
                            action: 'UPDATE',
                            originalName: nodeName,
                            attributes: {
                                x: newX,
                                y: newY
                            }
                        };
                        sendServerRequest(updateData);
                    }

                    selectedElement = null;
                    svg.removeEventListener('mousemove', drag);
                    svg.removeEventListener('mouseup', endDrag);
                }

                // --- EDITING FUNCTIONS ---
                // --- NEW DIALOG TRIGGER FUNCTION ---
                function triggerJavaEditDialog(element) {
                    var nodeName = element.getAttribute('data-node-name');
                    var nodeType = element.getAttribute('data-node-type');

                    // 💡 The Java method will now handle everything else.
                    if (window.xmlEditorBridge && typeof window.xmlEditorBridge.openEditDialogForNode === 'function') {
                        window.xmlEditorBridge.openEditDialogForNode(nodeName, nodeType);
                    } else {
                        console.error("Java bridge method openEditDialogForNode not found.");
                    }
                }
                
                function openEditPanel(element) {
                    var panelContainer = document.getElementById('editPanelContainer');
                    var form = document.getElementById('attributeForm');
                    var nodeName = element.getAttribute('data-node-name');
                    var nodeType = element.getAttribute('data-node-type');

                    // Position and display the panel
                    var bbox = element.getBoundingClientRect();
                    var svgBox = svg.getBoundingClientRect();

                    // Position panel relative to the SVG container
                    panelContainer.style.left = (bbox.right + 20 - svgBox.left) + 'px';
                    panelContainer.style.top = (bbox.top - svgBox.top) + 'px';
                    panelContainer.style.display = 'block';

                    document.getElementById('panelTitle').textContent = `Editing: ${nodeType} (${nodeName})`;
                    document.getElementById('editPanel').setAttribute('data-target-name', nodeName);

                    // Clear previous form fields
                    form.innerHTML = '<input type="hidden" id="nodeOriginalName" value="' + nodeName + '"/>';

                    // Inject form fields for attributes and text content
                    var attributes = ['name', 'x', 'y', 'width', 'height', 'label', 'value', 'text']; // Common attributes

                    attributes.forEach(attr => {
                        var value = element.getAttribute(`data-original-${attr}`);
                        if (value !== null) {
                            var inputHTML = `
                                <label style="display: block; font-size: 10px;">${attr}:</label>
                                <input type="text" id="attr_${attr}" value="${value}" style="width: 150px; margin-bottom: 5px;"/>
                            `;
                            form.insertAdjacentHTML('beforeend', inputHTML);
                        }
                    });
                }

                function saveNodeAttributes() {
                    var panel = document.getElementById('editPanel');
                    var originalName = panel.getAttribute('data-target-name');
                    var form = document.getElementById('attributeForm');

                    var data = {
                        action: 'UPDATE',
                        originalName: originalName,
                        attributes: {}
                    };

                    for (var i = 0; i < form.elements.length; i++) {
                        var element = form.elements[i];
                        if (element.id.startsWith('attr_')) {
                            var attrName = element.id.substring(5);
                            data.attributes[attrName] = element.value;
                        }
                    }

                    sendServerRequest(data);
                    document.getElementById('editPanelContainer').style.display='none';
                }

                function deleteNodeHandler() {
                    var nodeName = document.getElementById('editPanel').getAttribute('data-target-name');
                    if (confirm(`Are you sure you want to delete node "${nodeName}"?`)) {
                        sendServerRequest({
                            action: 'DELETE',
                            name: nodeName
                        });
                        document.getElementById('editPanelContainer').style.display='none';
                    }
                }

                function addNewNodeHandler() {
                    var parentNodeName = document.getElementById('editPanel').getAttribute('data-target-name');
                    var newNodeType = prompt("Enter the XML tag name for the new child node (e.g., 'TextBox', 'Item'):");

                    if (newNodeType && parentNodeName) {
                        sendServerRequest({
                            action: 'ADD',
                            parentName: parentNodeName,
                            newNodeType: newNodeType
                        });
                        document.getElementById('editPanelContainer').style.display='none';
                    }
                }

                // --- SERVER INTERACTION (Java Bridge) ---
                function sendServerRequest(data) {
                    var jsonString = JSON.stringify(data);

                    // 💡 Check if the bridge object exists AND the method exists on it.
                    if (window.xmlEditorBridge && typeof window.xmlEditorBridge.handleJsUpdateRequest === 'function') {
                        // Call the public Java method
                        window.xmlEditorBridge.handleJsUpdateRequest(jsonString);
                        console.log("Called Java bridge with data:", data.action);
                    } else {
                        console.error("Java bridge (window.xmlEditorBridge) not available. Cannot send request:", jsonString);
                        alert("Cannot update data: Java bridge not connected.");
                    }
                }
            ]]>
            </script>
            <xsl:call-template name="info-section">
                <xsl:with-param name="start-y" select="$PADDING"/>
                <xsl:with-param name="line-height" select="$LINE_HEIGHT"/>
            </xsl:call-template>

            <xsl:variable name="RECT_Y_START" select="$INFO_HEIGHT_CALCULATED + $PADDING"/>

            <xsl:apply-templates select="/*/*" mode="child-rects">
                <xsl:with-param name="absolute-origin-x" select="$PADDING"/>
                <xsl:with-param name="absolute-origin-y" select="$RECT_Y_START"/>
            </xsl:apply-templates>

            <foreignObject x="0" y="0" width="400" height="400" id="editPanelContainer" style="display:none; overflow: visible;">
                <div xmlns="http://www.w3.org/1999/xhtml" id="editPanel" style="position: absolute; top: 0; left: 0; padding: 10px; border: 1px solid #4682B4; background-color: #F0F8FF; box-shadow: 2px 2px 5px rgba(0,0,0,0.5); z-index: 1000; width: 200px;">
                    <h3 id="panelTitle" style="margin: 0 0 10px 0; font-size: 14px;">Edit Node Attributes</h3>
                    <form id="attributeForm" style="margin-bottom: 10px;">
                    </form>
                    <div style="border-top: 1px solid #ccc; padding-top: 5px;">
                        <button onclick="saveNodeAttributes()" style="background-color: #4CAF50; color: white; border: none; padding: 5px; cursor: pointer;">Save</button>
                        <button onclick="deleteNodeHandler()" style="background-color: #f44336; color: white; border: none; padding: 5px; cursor: pointer;">Delete</button>
                        <button onclick="addNewNodeHandler()" style="background-color: #2196F3; color: white; border: none; padding: 5px; cursor: pointer;">Add Child</button>
                        <button onclick="document.getElementById('editPanelContainer').style.display='none'" style="background-color: #888; color: white; border: none; padding: 5px; cursor: pointer;">Close</button>
                    </div>
                </div>
            </foreignObject>

        </svg>
    </xsl:template>

    ---

    <xsl:template name="info-section">
        <xsl:param name="start-y" select="0"/>
        <xsl:param name="line-height" select="15"/>

        <xsl:variable name="y1" select="$start-y + $line-height"/>
        <xsl:variable name="y2" select="$start-y + (2 * $line-height)"/>
        <xsl:variable name="y3" select="$start-y + (3 * $line-height)"/>

        <text x="{$TEXT_X}" y="{$y1}" font-size="16" font-weight="bold" fill="black">
            Selected Node: <xsl:value-of select="name(/*)"/>
        </text>

        <xsl:if test="/*/@name">
            <text x="{$TEXT_X}" y="{$y2}" font-size="14" fill="blue">
                Name: <xsl:value-of select="/*/@name"/>
            </text>
        </xsl:if>

        <text x="{$TEXT_X}" y="{$y3}" font-size="12" fill="red">
            Attributes:
        </text>

        <xsl:for-each select="/*/@*">
            <text x="{$TEXT_X + 10}" y="{$y3 + position() * $line-height}" font-size="10" fill="red">
                <xsl:value-of select="name()"/>="<xsl:value-of select="."/>"
            </text>
        </xsl:for-each>
    </xsl:template>

    ---

    <xsl:template match="*" mode="child-rects">
        <xsl:param name="absolute-origin-x" select="0"/>
        <xsl:param name="absolute-origin-y" select="0"/>

        <xsl:variable name="x-val" select="number(@x)"/>
        <xsl:variable name="y-val" select="number(@y)"/>

        <xsl:variable name="w-val">
            <xsl:choose>
                <xsl:when test="number(@width) &gt; 0"><xsl:value-of select="number(@width)"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="$DEFAULT_CHILD_DIM"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="h-val">
            <xsl:choose>
                <xsl:when test="number(@height) &gt; 0"><xsl:value-of select="number(@height)"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="$DEFAULT_CHILD_DIM"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="x-pos" select="$x-val + $absolute-origin-x"/>
        <xsl:variable name="y-pos" select="$y-val + $absolute-origin-y"/>

        <xsl:variable name="text-content">
            <xsl:choose>
                <xsl:when test="normalize-space(text()[1]) != ''">
                    <xsl:value-of select="normalize-space(text()[1])"/>
                </xsl:when>
                <xsl:otherwise></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:if test="@x or @y">
            <g class="child-node"
               onmousedown="startDrag(evt)"
               onclick="openEditPanel(this)"
               transform="translate(0,0)"
               data-node-name="{@name}"
               data-node-type="{name()}"
               data-original-x="{$x-val}"
               data-original-y="{$y-val}"
               data-original-width="{number(@width)}"
               data-original-height="{number(@height)}"
               data-original-label="{@label}"
               data-original-value="{@value}"
               data-original-text="{$text-content}">

                <rect x="{$x-pos}" y="{$y-pos}"
                      width="{$w-val}" height="{$h-val}"
                      style="fill:#B0C4DE; stroke:#4682B4; stroke-width:2; opacity:0.8; cursor: pointer;"/>

                <text x="{$x-pos + 5}" y="{$y-pos + 15}" font-size="10" fill="black">
                    <xsl:value-of select="name()"/>: <xsl:value-of select="@name"/>
                </text>
            </g>
        </xsl:if>
    </xsl:template>

    <xsl:template match="*"/>
    <xsl:template match="@*"/>

</xsl:stylesheet>