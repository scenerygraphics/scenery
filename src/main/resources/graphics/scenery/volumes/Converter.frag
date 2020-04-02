uniform vec4 offset;
uniform vec4 scale;

float convert( float v )
{
    return offset.a + scale.a * v;
}
